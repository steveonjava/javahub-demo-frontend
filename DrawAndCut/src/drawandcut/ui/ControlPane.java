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
package drawandcut.ui;

import static drawandcut.Configuration.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

/**
 *
 * @author akouznet
 */
public class ControlPane extends GridPane {


    private final Button scan = new Button("Scan");
    private final Button draw = new Button("Draw");
    private final Button cut = new Button("Cut");
    private final Button load = new Button("Load");
    private final Button exit = new Button("Exit");

    public ControlPane() {
        scan.setId("scan");
        scan.setPrefSize(BUTTON_PREF_WIDTH, BUTTON_PREF_HEIGHT);
        draw.setId("draw");
        draw.setPrefSize(BUTTON_PREF_WIDTH, BUTTON_PREF_HEIGHT);
        load.setId("load");
        load.setPrefSize(BUTTON_PREF_WIDTH, BUTTON_PREF_HEIGHT);
        cut.setId("cut");
        cut.setPrefSize(BUTTON_PREF_WIDTH, BUTTON_PREF_HEIGHT);
        exit.setId("exit");
        exit.setPrefSize(BUTTON_PREF_WIDTH, BUTTON_PREF_HEIGHT);
        
        setId("controlPane");
        setPadding(new Insets(PADDING));
        setVgap(PADDING);
        setAlignment(Pos.CENTER);
        addRow(0, scan);
        addRow(1, draw);
        addRow(2, load);
        addRow(3, cut);
        addRow(4, exit);
    }
    
    public Button cutButton() {
        return cut;
    }

    public Button loadButton() {
        return load;
    }
    
    public Button scanButton() {
        return scan;
    }
    
    public Button drawButton() {
        return draw;
    }
    
    public Button exitButton() {
        return exit;
    }
    
}
