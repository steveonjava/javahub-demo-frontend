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

import drawandcut.Configuration;
import drawandcut.scanner.QRCodeScanner;
import java.util.function.Consumer;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 *
 * @author akouznet
 */
public class ScannerPane extends BorderPane {
    
    private final QRCodeScanner codeScanner = new QRCodeScanner();
    private Consumer<String> onRead;
    private int counter;
    
    public ScannerPane() {
        setBackground(new Background(new BackgroundFill(Color.DARKGRAY, CornerRadii.EMPTY,
                Insets.EMPTY)));
        
        Label title = new Label("Scan QR code");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font(25));
        BorderPane.setAlignment(title, Pos.CENTER);
        
        if (Configuration.DISABLE_CAMERA) {
            Button scan = new Button("\"Scan\" bird");
            scan.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            scan.setOnAction(t -> {
                if (onRead != null) {
                    onRead.accept("bird");
                }
            });
            setCenter(scan);
        }
        
        setTop(title);
    }

    public void start() {
        counter = 0;
        System.out.println("ScannerPane.start()");
        Bounds previewBounds = localToScreen(getBoundsInLocal());
        codeScanner.setPreviewPosition(
                (int) Math.round(previewBounds.getMinX()), 
                (int) Math.round(previewBounds.getMinX()), 
                (int) Math.round(previewBounds.getWidth()), 
                (int) Math.round(previewBounds.getHeight()));
        codeScanner.startTakingStillImages(getBoundsInLocal().getWidth(), getBoundsInLocal().getHeight(), (image, code) -> {
            System.out.println((counter++) + ". image = " + image);
            if (onRead != null) {
                onRead.accept(code);
            }
        }, System.err::println);
    }
    
    public void stop() {
        System.out.println("ScannerPane.stop()");
        codeScanner.stopTakingStillImages();
    }
    
    public void setOnRead(Consumer<String> onRead) {
        this.onRead = onRead;
    }
}
