package app.bpartners.geojobs.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

/**
 * Line simplification using the Ramer–Douglas–Peucker algorithm. Reference: <a
 * href="https://rosettacode.org/wiki/Ramer-Douglas-Peucker_line_simplification#Java">...</a>
 */
@RequiredArgsConstructor
public class PolylineSimplifier {
  private final double epsilon;

  public List<Coordinate> simplifyLine(List<Coordinate> coordinates) {
    List<Coordinate> result = new ArrayList<>();

    ramerDouglasPeucker(coordinates, epsilon, result);

    return result;
  }

  private static List<Coordinate> toPolyline(Polygon polygon) {
    return List.of(polygon.getCoordinates()).subList(0, polygon.getCoordinates().length - 1);
  }

  public Polygon simplifyPolygon(Polygon polygon) {
    var simplifiedLine = new ArrayList<>(simplifyLine(toPolyline(polygon)));
    simplifiedLine.add(simplifiedLine.getFirst());

    return geometryFactory.createPolygon(simplifiedLine.toArray(Coordinate[]::new));
  }

  private static double perpendicularDistance(
      Coordinate pt, Coordinate lineStart, Coordinate lineEnd) {
    double dx = lineEnd.getX() - lineStart.getX();
    double dy = lineEnd.getY() - lineStart.getY();

    // Normalize
    double mag = Math.hypot(dx, dy);
    if (mag > 0.0) {
      dx /= mag;
      dy /= mag;
    }

    double pvx = pt.getX() - lineStart.getX();
    double pvy = pt.getY() - lineStart.getY();

    // Get dot product (project pv onto normalized direction)
    double pvdot = dx * pvx + dy * pvy;

    // Scale line direction vector and subtract it from pv
    double ax = pvx - pvdot * dx;
    double ay = pvy - pvdot * dy;

    return Math.hypot(ax, ay);
  }

  private static void ramerDouglasPeucker(
      List<Coordinate> pointList, double epsilon, List<Coordinate> out) {
    if (pointList.size() < 2) {
      throw new IllegalArgumentException("Not enough points to simplify");
    }

    // Find the point with the maximum distance from line between the start and end
    double dmax = 0.0;
    int index = 0;
    int end = pointList.size() - 1;
    for (int i = 1; i < end; ++i) {
      double d = perpendicularDistance(pointList.get(i), pointList.getFirst(), pointList.get(end));
      if (d > dmax) {
        index = i;
        dmax = d;
      }
    }

    // If max distance is greater than epsilon, recursively simplify
    if (dmax < epsilon) {

      // Just return start and end points
      out.clear();
      out.add(pointList.getFirst());
      out.add(pointList.getLast());
      return;
    }

    List<Coordinate> recResults1 = new ArrayList<>();
    List<Coordinate> recResults2 = new ArrayList<>();
    List<Coordinate> firstLine = pointList.subList(0, index + 1);
    List<Coordinate> lastLine = pointList.subList(index, pointList.size());
    ramerDouglasPeucker(firstLine, epsilon, recResults1);
    ramerDouglasPeucker(lastLine, epsilon, recResults2);

    // build the result list
    out.addAll(recResults1.subList(0, recResults1.size() - 1));
    out.addAll(recResults2);
    if (out.size() < 2) {
      throw new IllegalArgumentException("Problem assembling output");
    }
  }
}
