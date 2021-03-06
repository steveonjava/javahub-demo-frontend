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
package drawandcut;

import drawandcut.cutter.Cutter;
import drawandcut.cutter.CutterConnection;
import drawandcut.gcode.PathConverter;
import drawandcut.ui.ControlPane;
import drawandcut.ui.DrawPane;
import drawandcut.ui.ScannerPane;
import drawandcut.ui.ShapesPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Scanner;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.paint.Color;
import drawandcut.gcode.SurfaceEvener;
import drawandcut.ui.ExitPopup;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import java.util.logging.Level;
import java.util.logging.Logger;

import static drawandcut.Configuration.*;

/**
 * @author akouznet
 */
public class DrawAndCut extends Application {
    private CutterConnection cutterConnection;
    private final Shapes shapes = new Shapes();
    private Scene drawScene;
    private ControlPane controlPane;
    private ScannerPane scannerPane;
    private ShapesPane shapesPane;
    private DrawPane drawPane;
    private BorderPane borderPane;
    private Task<String> downloadTask;
    private ExitPopup exitPopup;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        cutterConnection = new CutterConnection();
        if (!DISABLE_CUTTER) {
            try {
                cutterConnection.connectToCutter();
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(-1);
            }
        }

        borderPane = new BorderPane();
        borderPane.setBackground(Background.EMPTY);
        borderPane.setPadding(new Insets(
                SCREEN_PADDING_TOP,
                SCREEN_PADDING_RIGHT,
                SCREEN_PADDING_BOTTOM,
                SCREEN_PADDING_LEFT));

        scannerPane = new ScannerPane();
        scannerPane.setPadding(new Insets(PADDING));

        drawPane = new DrawPane();
        drawPane.setFocusTraversable(true);
        drawPane.setPadding(new Insets(PADDING));
        borderPane.setCenter(drawPane);

        shapesPane = new ShapesPane(shapes);
        shapesPane.setOnAction(shapeOrCut -> {
            if (shapeOrCut instanceof Shapes.Shape) {
                Shapes.Shape shape = (Shapes.Shape) shapeOrCut;
                drawPane.importSVG(shape.getSvg(), shape.getSize(), DrawPane.ImportSource.MODEL);
            } else {
                Cut cut = (Cut) shapeOrCut;
                drawPane.importCut(cut);
            }
            showDrawPane();
        });

        controlPane = new ControlPane();
        borderPane.setLeft(controlPane);

        ToggleGroup tg = new ToggleGroup();
        tg.getToggles().addAll(
                controlPane.drawButton(),
                controlPane.loadButton(),
                controlPane.scanButton());

        controlPane.loadButton().setOnAction(t -> showLoadPane());
        controlPane.cutButton().disableProperty()
                .bind(drawPane.outlineProperty().isNull()
                        .or(NO_HOLE
                                ? Bindings.createBooleanBinding(() -> false)
                                : drawPane.holeProperty().isNull())
                        .or(borderPane.centerProperty().isEqualTo(scannerPane))
                        .or(DISABLE_CUTTER
                                ? Bindings.createBooleanBinding(() -> false)
                                : cutterConnection.getCutter().ready().not()));
        controlPane.cutButton().setOnAction(t -> {
            List<String> output = new PathConverter(
                    drawPane.outlineProperty().get(),
                    drawPane.holeProperty().get(),
                    drawPane.getInitials(),
                    RPM, FEED, DOC, PLUNGE_FEED).getOutput();
            shapesPane.addCut(drawPane.getCut());
//            System.out.println("Program:");
//            for(String line : output) {
//                System.out.println(line);
//            }
            try {
                Files.write(new File("output.nc").toPath(), output);
            } catch (IOException ex) {
                Logger.getLogger(DrawAndCut.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
            Cutter cutter = cutterConnection.getCutter();
            if (cutter != null) {
                cutter.sendSequence(output.toArray(new String[output.size()]));
            }
        });
        controlPane.exitButton().setOnAction(x -> {
            scannerPane.stop();
            if (exitPopup == null) {
                exitPopup = new ExitPopup();
                exitPopup.exit.setOnAction(t -> System.exit(0));
                exitPopup.restart.setOnAction(t -> System.exit(25));
                exitPopup.reboot.setOnAction(t -> reboot());
                exitPopup.poweroff.setOnAction(t -> poweroff());
            }
            exitPopup.setOnHidden(t -> {                
                controlPane.exitButton().setSelected(false);
                if (borderPane.getCenter() == scannerPane) {
                    scannerPane.start();
                }
            });
            Bounds b = controlPane.exitButton().localToScreen(controlPane.exitButton().getBoundsInLocal());
            exitPopup.show(primaryStage, 
                    b.getMaxX() + PADDING, 
                    b.getMinY() - PADDING);
        });
        
        controlPane.evenButton().setOnAction(t -> {
            List<String> output = new SurfaceEvener(
                    RPM, FEED, PLUNGE_FEED).getOutput();
            try {
                Files.write(new File("surfaceEvener.nc").toPath(), output);
            } catch (IOException ex) {
                Logger.getLogger(DrawAndCut.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
            Cutter cutter = cutterConnection.getCutter();
            if (cutter != null) {
                cutter.sendSequence(output.toArray(new String[output.size()]));
            }
        });

        primaryStage.setTitle("JavaOne2016 - Draw and Cut demo");
        drawScene = new Scene(borderPane, SCREEN_WIDTH, SCREEN_HEIGHT, Color.BLACK);

//        controlPane.scanButton().setDisable(Configuration.DISABLE_CAMERA);
        controlPane.scanButton().setOnAction(t -> {
            showScannerPane();
        });
        controlPane.drawButton().setOnAction(t -> {
            if (borderPane.getCenter() == drawPane) {
                drawPane.reset();
                drawPane.drawShape();
            }
            showDrawPane();
            controlPane.drawButton().setSelected(true);
        });
        controlPane.drawButton().setSelected(true);
        
        scannerPane.setOnRead(uuid -> {
            downloadTask = new Task<String>() {
                        @Override
                        protected String call() throws Exception {
                            return readUrlToString(CLOUD_BASE_URL + uuid);
                        }
                    };
            new Thread(downloadTask, "SVG path downloader").start();
            scannerPane.showProgress().bind(downloadTask.runningProperty());
            downloadTask.valueProperty().addListener(t -> {
                String svg = downloadTask.getValue();
                if (svg != null) {
                    drawPane.importSVG(svg, MATERIAL_SIZE_X, DrawPane.ImportSource.WEBAPP);
                    showDrawPane();
                }
            });
            downloadTask.setOnFailed(e -> {
                Throwable ex = downloadTask.getException();
                if (ex instanceof FileNotFoundException) {
                    scannerPane.setTitle("Bad QR code");
                } else if (ex instanceof NoRouteToHostException || ex instanceof ConnectException) {
                    scannerPane.setTitle("No connection to server");
                } else if (ex instanceof IOException) {
                    Matcher matcher
                            = Pattern.compile("Server returned HTTP response code: ([0-9]{3}) for URL: ").matcher(ex.getMessage());
                    if (matcher.find()) {
                        int errorCode = Integer.parseInt(matcher.group(1));
                        scannerPane.setTitle("Server returned " + errorCode);
                    } else {
                        scannerPane.setTitle("Load failed with " + ex.getClass().getSimpleName());
                    }
                } else {
                    scannerPane.setTitle("Load failed with " + ex.getClass().getSimpleName());
                }
            });
        });
        
        primaryStage.setScene(drawScene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> System.exit(0));
        drawScene.getStylesheets().add(
                DrawAndCut.class.getResource("styles.css").toExternalForm());

//        Path path = new Path(new MoveTo(0, 0), new LineTo(100, 0), new LineTo(0, 50), new ClosePath());
//        Outliner outliner = new Outliner(path);
//        Path outline = outliner.generateOutline();
    }
    
    private static void reboot() {
        try {
            new ProcessBuilder("reboot", "now").inheritIO().start();
        } catch (IOException ex) {
            Logger.getLogger(DrawAndCut.class.getName()).log(Level.SEVERE, null,
                    ex);
        }
    }
    
    private static void poweroff() {
        try {
            new ProcessBuilder("poweroff").inheritIO().start();
        } catch (IOException ex) {
            Logger.getLogger(DrawAndCut.class.getName()).log(Level.SEVERE, null,
                    ex);
        }
    }
    
    private static String readUrlToString(String url) throws IOException {
        try (
                InputStream inputStream = new URL(url).openStream();
                Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A")
        ) {
            if (scanner.hasNext()) {
                return scanner.next();
            }
        } catch (IOException ex) {
            Logger.getLogger(DrawAndCut.class.getName())
                    .log(Level.SEVERE, null, ex);
            throw ex;
        }
        return null;
    }

    private void showDrawPane() {
        scannerPane.stop();
        if (downloadTask != null) {
            downloadTask.cancel();
        }
        borderPane.setCenter(drawPane);
        drawPane.requestFocus();
        controlPane.drawButton().setSelected(true);
    }

    private void showScannerPane() {
        borderPane.setCenter(scannerPane);
        scannerPane.start();
        scannerPane.requestFocus();
        controlPane.scanButton().setSelected(true);
    }

    private void showLoadPane() {
        scannerPane.stop();
        if (downloadTask != null) {
            downloadTask.cancel();
        }
        borderPane.setCenter(shapesPane);
        shapesPane.requestFocus();
        controlPane.loadButton().setSelected(true);
    }

}
