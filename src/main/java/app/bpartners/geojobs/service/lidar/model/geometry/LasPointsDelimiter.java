package app.bpartners.geojobs.service.lidar.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;

import app.bpartners.geojobs.model.geometry.PolylineSimplifier;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

@Slf4j
public class LasPointsDelimiter {
  private Polygon polygon;
  private final double concaveRatio;
  private final Collection<LasPointGeometry> points;
  private final PolylineSimplifier polylineSimplifier;

  public static final double DEFAULT_CONCAVE_RATIO = 0.1;
  public static final double DEFAULT_POLYLINE_SIMPLIFIER_EPSILON = 0.6;

  public LasPointsDelimiter(Collection<LasPointGeometry> points) {
    this(points, DEFAULT_CONCAVE_RATIO, DEFAULT_POLYLINE_SIMPLIFIER_EPSILON);
  }

  public LasPointsDelimiter(
      Collection<LasPointGeometry> points, double concaveRatio, double polylineSimplifierEpsilon) {
    this.points = points;
    this.concaveRatio = concaveRatio;
    this.polylineSimplifier = new PolylineSimplifier(polylineSimplifierEpsilon);
  }

  public Polygon getPolygon() {
    if (polygon == null) {
      polygon = polylineSimplifier.simplifyPolygon(getPolygon(points));
    }

    return polygon;
  }

  private Polygon getPolygon(Collection<LasPointGeometry> points) {
    var coordinates =
        points.stream().map(LasPointGeometry::getCoordinate).toArray(Coordinate[]::new);
    var multiPoint = geometryFactory.createMultiPointFromCoords(coordinates);

    var hull = ConcaveHull.concaveHullByLengthRatio(multiPoint, concaveRatio);

    if (!hull.isValid()) {
      log.warn("Concave hull produced an invalid geometry. Attempting to fix it with buffer(0).");
      hull = hull.buffer(0);
    }

    return (Polygon) hull;
  }
}
