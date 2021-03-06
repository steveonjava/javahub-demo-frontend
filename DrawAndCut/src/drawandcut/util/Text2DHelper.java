package drawandcut.util;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javafx.beans.binding.Bindings;
import javafx.geometry.VPos;

/**
 * @author José Pereda
 */
public class Text2DHelper {

    private final static int POINTS_CURVE = 10;

    private final String text;
    private List<Point3D> list;
    private Point3D p0;
    private final List<LineSegment> polis = new ArrayList<>();

    public Text2DHelper(String text, String font, double size, double maxWidth, double maxHeight) {
        this.text = text;
        list = new ArrayList<>();

        Text textNode = new Text(text);
        textNode.translateXProperty().bind(Bindings.createDoubleBinding(
                () -> -textNode.getBoundsInLocal().getMinX(),
                textNode.boundsInLocalProperty()));
        textNode.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> -textNode.getBoundsInLocal().getMinY(),
                textNode.boundsInLocalProperty()));
        textNode.setFont(new Font(font, size));

        // Convert Text to Path
        Path subtract = (Path) (Shape.intersect(textNode, new Rectangle(maxWidth, maxHeight)));
        // Convert Path elements into lists of points defining the perimeter (exterior or interior)
        subtract.getElements().forEach(this::getPoints);

        // Group exterior polygons with their interior polygons
        polis.stream().filter(LineSegment::isHole).forEach(hole -> {
            polis.stream().filter(poly -> !poly.isHole())
                    .filter(poly -> !((Path) Shape.intersect(poly.getPath(), hole.getPath())).getElements().isEmpty())
                    .filter(poly -> poly.getPath().contains(new Point2D(hole.getOrigen().getX(), hole.getOrigen().getY())))
                    .forEach(poly -> poly.addHole(hole));
        });
        polis.removeIf(LineSegment::isHole);
    }

    public List<LineSegment> getLineSegment() {
        return polis;
    }

    public List<Point3D> getOffset() {
        return polis.stream().sorted((p1, p2) -> (int) (p1.getOrigen().getX() - p2.getOrigen().getY()))
                .map(LineSegment::getOrigen).collect(Collectors.toList());
    }

    private void getPoints(PathElement elem) {
        if (elem instanceof MoveTo) {
            list = new ArrayList<>();
            p0 = new Point3D((float) ((MoveTo) elem).getX(), (float) ((MoveTo) elem).getY(), 0f);
            list.add(p0);
        } else if (elem instanceof LineTo) {
            list.add(new Point3D((float) ((LineTo) elem).getX(), (float) ((LineTo) elem).getY(), 0f));
        } else if (elem instanceof CubicCurveTo) {
            Point3D ini = (list.size() > 0 ? list.get(list.size() - 1) : p0);
            IntStream.rangeClosed(1, POINTS_CURVE).forEach(i -> list.add(evalCubicBezier((CubicCurveTo) elem, ini, ((double) i) / POINTS_CURVE)));
        } else if (elem instanceof QuadCurveTo) {
            Point3D ini = (list.size() > 0 ? list.get(list.size() - 1) : p0);
            IntStream.rangeClosed(1, POINTS_CURVE).forEach(i -> list.add(evalQuadBezier((QuadCurveTo) elem, ini, ((double) i) / POINTS_CURVE)));
        } else if (elem instanceof ClosePath) {
            list.add(p0);
            // Every closed path is a polygon (exterior or interior==hole)
            // the text, the list of points and a new path between them are
            // stored in a LineSegment: a continuous line that can change direction
            if (Math.abs(getArea()) > 0.001) {
                LineSegment line = new LineSegment(text);
                line.setHole(isHole());
                line.setPoints(list);
                line.setPath(generatePath());
                line.setOrigen(p0);
                polis.add(line);
            }
        }
    }

    private Point3D evalCubicBezier(CubicCurveTo c, Point3D ini, double t) {
        Point3D p = new Point3D((float) (Math.pow(1 - t, 3) * ini.getX() +
                3 * t * Math.pow(1 - t, 2) * c.getControlX1() +
                3 * (1 - t) * t * t * c.getControlX2() +
                Math.pow(t, 3) * c.getX()),
                (float) (Math.pow(1 - t, 3) * ini.getY() +
                        3 * t * Math.pow(1 - t, 2) * c.getControlY1() +
                        3 * (1 - t) * t * t * c.getControlY2() +
                        Math.pow(t, 3) * c.getY()),
                0f);
        return p;
    }

    private Point3D evalQuadBezier(QuadCurveTo c, Point3D ini, double t) {
        Point3D p = new Point3D((float) (Math.pow(1 - t, 2) * ini.getX() +
                2 * (1 - t) * t * c.getControlX() +
                Math.pow(t, 2) * c.getX()),
                (float) (Math.pow(1 - t, 2) * ini.getY() +
                        2 * (1 - t) * t * c.getControlY() +
                        Math.pow(t, 2) * c.getY()),
                0f);
        return p;
    }

    private double getArea() {
        DoubleProperty res = new SimpleDoubleProperty();
        IntStream.range(0, list.size() - 1)
                .forEach(i -> res.set(res.get() + list.get(i).crossProduct(list.get(i + 1)).getZ()));
        // System.out.println("path: "+res.doubleValue()/2);

        return res.doubleValue() / 2d;
    }

    private boolean isHole() {
        // area>0 -> the path is a hole, clockwise (y up)
        // area<0 -> the path is a polygon, counterclockwise (y up)
        return getArea() > 0;
    }

    private Path generatePath() {
        Path path = new Path(new MoveTo(list.get(0).getX(), list.get(0).getY()));
        list.stream().skip(1).forEach(p -> path.getElements().add(new LineTo(p.getX(), p.getY())));
        path.getElements().add(new ClosePath());
        path.setStroke(Color.GRAY);
        // Path must be filled to allow Shape.intersect
        path.setFill(Color.WHITE);
        return path;
    }
}