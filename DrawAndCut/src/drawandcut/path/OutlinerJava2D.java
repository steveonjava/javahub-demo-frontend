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
package drawandcut.path;

import drawandcut.Configuration;
import static drawandcut.Configuration.*;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/**
 *
 * @author akouznet
 */
public class OutlinerJava2D implements Outliner {
            
    @Override
    public Path generateOutline(Path path) {
        Path2D path2D = PathConversions.convertToPath2D(path);
        BasicStroke basicStroke = new BasicStroke(
                (float) (MOTIF_WIDTH_MM + TOOL_DIAMETER), 
                BasicStroke.CAP_ROUND, 
                BasicStroke.JOIN_ROUND);
        Shape strokedShape = basicStroke.createStrokedShape(path2D);
        Area area = new Area(strokedShape);
        PathIterator pathIterator = area.getPathIterator(null, Configuration.FLATNESS);
        Path outline = PathConversions.convertToPath(pathIterator);
        int pathCount = (int) outline.getElements().stream().filter(elem -> elem instanceof MoveTo).count();
        log("pathCount = " + pathCount);
        if (pathCount != 2) {
            throw new IllegalArgumentException("Path has intersections or has no interior");
        }   
        return outline;
    }
    
    @Override
    public Path generateFilledOutline(Path path) {
        Path2D path2D = PathConversions.convertToPath2D(path);        
        log("path2D.getBounds2D() = " + path2D.getBounds2D());
        BasicStroke basicStroke = new BasicStroke(
                (float) (Configuration.TOOL_DIAMETER/* * 0.75*/), 
                BasicStroke.CAP_ROUND, 
                BasicStroke.JOIN_ROUND);
        Shape strokedShape = basicStroke.createStrokedShape(path2D);
        Area area = new Area(strokedShape);
        area.add(new Area(path2D));        
        PathIterator pathIterator = area.getPathIterator(null, Configuration.FLATNESS);
        Path outline = PathConversions.convertToPath(pathIterator);
        int pathCount = (int) outline.getElements().stream().filter(elem -> elem instanceof MoveTo).count();
        log("pathCount = " + pathCount);
        log("outline.getBoundsInLocal() = " + outline.getBoundsInLocal());
//        if (pathCount != 2) {
//            throw new IllegalArgumentException("The path cannot have intersections or have no interior outline");
//        }   
        return outline;
    }
}