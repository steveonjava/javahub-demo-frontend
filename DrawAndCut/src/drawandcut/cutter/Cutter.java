/*
 * The MIT License
 *
 * Copyright 2016 Oracle.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package drawandcut.cutter;

import com.willwinder.universalgcodesender.GrblController;
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.types.GcodeCommand;
import static drawandcut.Configuration.PROBING_OFFSET;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javax.vecmath.Point3d;

/**
 *
 * @author akouznet
 */
public class Cutter {

    private String state;
    private final Point3d machineCoord = new Point3d();
    private final Point3d workCoord = new Point3d();
    private final CutterListener listener = new CutterListener();
    private volatile InitSequenceState initState = InitSequenceState.NOT_CONNECTED;
    private GrblController grblController;

    public void bindToController(GrblController grblController) {
        this.grblController = grblController;
        this.grblController.addListener(listener);
    }
    
    private enum InitSequenceState {
        NOT_CONNECTED,
        CONNECTED,
        HOMING,
        PROBING1,
        PROBING2,
        PROBING3,
        READY,
        COORDINATE_RESET,
        FAILED
    }
    
    private final String[] PROBE1 = { "G4P0.005", "M05", "G92.1", "G54", "G10 L2 P1 X0 Y0 Z0", "G21", "G49", "G90", "G10 L2 P1 X0 Y0 Z0", "G0 X-2.5 Z-5", "G0 Z-35.000", "G38.2Z-105 F800", "G4P0.005" };
    private final String[] PROBE2 = { "G0 Z-70", "G38.2Z-182.675F200.0", "G4P0.005" };
    private final String[] PROBE3 = { "G0 Z-5", "G0 X-5" };
    private final String COORDINATE_RESET_TEMPLATE = "G10 P0 L20 X220 Y205"; // Z is added based on PRB_Z
    private final String[] COORDINATE_RESET = { COORDINATE_RESET_TEMPLATE };
    private double prbZ = Double.NaN; // Tool measurement Z

    private class CutterListener implements ControllerListener {

        @Override
        public void fileStreamComplete(String filename, boolean success) {
            System.out.println(
                    "ControllerListener.fileStreamComplete-1 filename = "
                    + filename + ", success = " + success);
            Platform.runLater(() -> {
                System.out.println(
                        "ControllerListener.fileStreamComplete-2 filename = "
                        + filename + ", success = " + success);
                switch (initState) {
                    case PROBING1:
                        initState = InitSequenceState.PROBING2;
                        PROBE2[0] = "G0Z" + (machineCoord.z + 5);
                        System.out.println("PROBE2 = " + Arrays.toString(PROBE2));
                        sendSequence(PROBE2);
                        break;
                    case PROBING2:
                        initState = InitSequenceState.PROBING3;
                        sendSequence(PROBE3);
                        break;
                    case PROBING3:
                        initState = InitSequenceState.COORDINATE_RESET;
                        if (COORDINATE_RESET[0].equals(COORDINATE_RESET_TEMPLATE)) {
                            throw new IllegalStateException("Coordinates were not initialized properly!");
                        }
                        sendSequence(COORDINATE_RESET);
                        break;
                    case COORDINATE_RESET:
                        initState = InitSequenceState.READY;
                        break;
                }
                printState();
            });
        }

        public void printState() {
            System.out.println("initState = " + initState);
            System.out.println("state = " + state);
            System.out.println("workCoord = " + workCoord);
            System.out.println("machineCoord = " + machineCoord);
        }

        @Override
        public void commandSent(GcodeCommand command) {
            System.out.println("ControllerListener.commandSent command = "
                    + command);
        }

        @Override
        public void commandComplete(GcodeCommand command) {
            try {
                System.out.println("ControllerListener.commandComplete command = "
                        + command);
                switch (initState) {
                    case NOT_CONNECTED:
                        initState = InitSequenceState.CONNECTED;
                        break;
                    case CONNECTED:
                        initState = InitSequenceState.HOMING;
                        grblController.performHomingCycle();
                        break;
                    case HOMING:
                        initState = InitSequenceState.PROBING1;
                        sendSequence(PROBE1);
                        break;
                }
                printState();
            } catch (Exception ex) {
                Logger.getLogger(Cutter.class.getName()).log(Level.SEVERE, null,
                        ex);
                initState = InitSequenceState.FAILED;
            }
        }

        @Override
        public void commandComment(String comment) {
            System.out.println("ControllerListener.commandComment comment = "
                    + comment);
        }

        @Override
        public void messageForConsole(String msg, Boolean verbose) {
            
//            if (type != ControllerListener.MessageType.VERBOSE 
//                    || msg.startsWith("GrblFeedbackMessage")) {
                
                System.out.print("CutterConnection verbose = " + verbose + ", " + msg);
//            }
            // parse GrblFeedbackMessage{message='[PRB:-2.500,-5.000,-84.405:1]', distanceMode='null', units='null'}
            if (!verbose && msg.startsWith("[PRB:")) {
                String pattern = "\\[PRB\\:-[0-9]*\\.[0-9]*,-[0-9]*\\.[0-9]*,(-[0-9]*\\.[0-9]*)\\:1\\]";
                Matcher matcher = Pattern.compile(pattern).matcher(msg);
                if (matcher.find()) {
//                    System.out.println("matcher = " + matcher);
//                    System.out.println("matcher.group(1) = " + matcher.group(1));
                    prbZ = Double.parseDouble(matcher.group(1));
                    System.out.println("prbZ = " + prbZ);
                    COORDINATE_RESET[0] = COORDINATE_RESET_TEMPLATE + " Z" + (PROBING_OFFSET - prbZ);
//                    System.out.println("COORDINATE_RESET[0] = " + COORDINATE_RESET[0]);
                }
            }
            if (!verbose && msg.contains("**** Connected to ") && initState == InitSequenceState.NOT_CONNECTED) {
                initState = InitSequenceState.CONNECTED;
            } else if (!verbose && msg.contains("['$H'|'$X' to unlock]") && initState == InitSequenceState.CONNECTED) {
                try {
                    initState = InitSequenceState.HOMING;
                    grblController.performHomingCycle();
                } catch (Exception ex) {
                    Logger.getLogger(Cutter.class.getName())
                            .log(Level.SEVERE, null, ex);
                }
            } else if (!verbose && msg.contains("error")) {
                throw new IllegalStateException("Error happened");
            }
        }

        @Override
        public void statusStringListener(String state, Point3d machineCoord,
                Point3d workCoord) {
            System.out.println("ControllerListener.statusStringListener state = " + state + ", machineCoord = " + machineCoord + ", workCoord = " + workCoord);
            Cutter.this.state = state;
            Cutter.this.machineCoord.set(machineCoord);
            Cutter.this.workCoord.set(workCoord);
        }

        @Override
        public void postProcessData(int numRows) {
            System.out.println("ControllerListener.postProcessData numRows = "
                    + numRows);
        }

        @Override
        public void commandQueued(GcodeCommand command) {
            System.out.println("ControllerListener.commandQueued command = "
                    + command);
        }

    }

    public void sendSequence(String[] sequence) {
        try {
            for (String command : sequence) {
                // I've tried sendImmediately here but it is not very reliable
                grblController.queueCommand(command);
            }
            grblController.beginStreaming();
        } catch (Exception ex) {
            Logger.getLogger(Cutter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
