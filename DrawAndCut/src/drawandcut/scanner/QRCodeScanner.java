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
package drawandcut.scanner;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.hopding.jrpicam.RPiCamera;
import com.hopding.jrpicam.exceptions.FailedToRunRaspistillException;
import static drawandcut.Configuration.DISABLE_CAMERA;
import static drawandcut.Configuration.log;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/**
 *
 * @author akouznet
 */
public class QRCodeScanner {
    
    public RPiCamera piCamera;

    public QRCodeScanner() {
        init();
    }
    
    private void init() {
        log("QRCodeScanner.init()");
        if (!DISABLE_CAMERA) {
            try {
                piCamera = new RPiCamera("/home/pi/Pictures");

                //Set Camera to produce 500x400 images.
                piCamera.setWidth(500); 
                piCamera.setHeight(400);
    
//                //Adjust Camera's brightness setting.
//                piCamera.setBrightness(75);
    
//                //Set Camera's exposure.
//                piCamera.setExposure(Exposure.AUTO);
    
                //Set Camera's timeout.
                piCamera.setTimeout(1);
                
//                //Add Raw Bayer data to image files created by Camera.
//                piCamera.setAddRawBayer(true);
                
//                piCamera.turnOffPreview();

                //Sets all Camera options to their default settings, overriding any changes previously made.
//                piCamera.setToDefaults();
            } catch (FailedToRunRaspistillException ex) {
                Logger.getLogger(QRCodeScanner.class.getName())
                        .log(Level.SEVERE, null, ex);
                piCamera = null;
            }
        }
        log("piCamera = " + piCamera);
    }
    
    public void setPreviewPosition(int x, int y, int width, int height) {
        if (piCamera != null) {
            piCamera.turnOnPreview(x, y, width, height);
        }
    }
    
    public Optional<Image> takeImage() {
        log("takeImage()");
        if (piCamera != null) {
            try {
                Optional<BufferedImage> bufferedImageOptional = Optional.ofNullable(piCamera.takeBufferedStill());
                log("bufferedImage = " + bufferedImageOptional);
                return bufferedImageOptional.flatMap(bufferedImage -> {
                    WritableImage image = convertImage(bufferedImage);
                    log("image = " + image);
    //                PixelReader pixelReader = image.getPixelReader();
    //                PixelFormat pixelFormat
    //                        = pixelReader.getPixelFormat();
    //                System.out.println("pixelFormat = " + pixelFormat);
    //                for (int x = 0; x < 100; x++) {
    //                    for (int y = 0; y < 100; y++) {
    //                        System.out.print(pixelReader.getArgb(x, y) + " ");
    //                    }
    //                    System.out.println();
    //                }
                    return Optional.of(image);
                });
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(QRCodeScanner.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        }
        return Optional.empty();
    }

    private static WritableImage convertImage(BufferedImage bufferedImage) {
        return bufferedImage == null ? null : SwingFXUtils.toFXImage(bufferedImage, new WritableImage(bufferedImage.getWidth(), bufferedImage.getHeight()));
    }
    
    private final QRCodeReader reader = new QRCodeReader();
    
    public void startTakingStillImages(double width, double height, BiConsumer<Image, String> resultConsumer, Consumer<Throwable> errorConsumer) {
        if (piCamera != null) {
            piCamera.startTakingStillImages((int) Math.round(width), (int) Math.round(height), bufferedImage -> {
                try {
                    BinaryBitmap binaryBitmap
                            = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(bufferedImage)));
                    Result decode = reader.decode(binaryBitmap);
                    log("QR code decoded: " + decode);
                    piCamera.stop();
                    Platform.runLater(() -> {
                        resultConsumer.accept(convertImage(bufferedImage), decode.getText());
                    });
                } catch (NotFoundException | ChecksumException exception) {
                    log(exception.toString());
                } catch (FormatException ex) {
                    Logger.getLogger(QRCodeScanner.class.getName())
                            .log(Level.SEVERE, null, ex);
                }
            }, errorConsumer);            
        }
    }
    
    public void stopTakingStillImages() {
        if (piCamera != null) {
            piCamera.stop();
        }
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }
    private Thread thread;
}
