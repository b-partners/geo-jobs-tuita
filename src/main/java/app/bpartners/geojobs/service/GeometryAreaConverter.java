package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;

import app.bpartners.geojobs.repository.model.SurfaceUnit;
import lombok.RequiredArgsConstructor;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeometryAreaConverter {
  // Random valid coordinate used to create a square
  private static final double LONGITUDE_WGS84_ORIGIN = 2.3488617521431934;
  private static final double LATITUDE_WGS84_ORIGIN = 48.847279853714326;
  private static final double X_LAMBERT_93_ORIGIN = 652215.5454725178;
  private static final double Y_LAMBERT_93_ORIGIN = 6861000.964461092;

  private final GeometrySquareMeterArea projector;

  public Double apply(Double area, SurfaceUnit from, SurfaceUnit to) {
    if (from.equals(to)) {
      return area;
    }

    var origin = getOrigin(from);
    var squarePolygonWithArea = createSquarePolygonWithArea(origin, area);
    var projectedSquarePolygon = projector.project(squarePolygonWithArea, getCRS(from), getCRS(to));
    return projectedSquarePolygon.getArea();
  }

  private static Polygon createSquarePolygonWithArea(Coordinate origin, double area) {
    if (area <= 0) {
      throw new IllegalArgumentException("Area must be positive");
    }

    double side = Math.sqrt(area);
    double half = side / 2.0;

    var coordinates =
        new Coordinate[] {
          new Coordinate(origin.getX() - half, origin.getY() - half),
          new Coordinate(origin.getX() + half, origin.getY() - half),
          new Coordinate(origin.getX() + half, origin.getY() + half),
          new Coordinate(origin.getX() - half, origin.getY() + half),
          new Coordinate(origin.getX() - half, origin.getY() - half)
        };

    return geometryFactory.createPolygon(coordinates);
  }

  private static CoordinateReferenceSystem getCRS(SurfaceUnit unit) {
    return switch (unit) {
      case SQUARE_METER -> LAMBERT_93;
      case SQUARE_DEGREE -> WGS84;
    };
  }

  private static Coordinate getOrigin(SurfaceUnit unit) {
    return switch (unit) {
      case SQUARE_METER -> new Coordinate(X_LAMBERT_93_ORIGIN, Y_LAMBERT_93_ORIGIN);
      case SQUARE_DEGREE -> new Coordinate(LONGITUDE_WGS84_ORIGIN, LATITUDE_WGS84_ORIGIN);
    };
  }
}
