package app.bpartners.geojobs.endpoint.rest.postprocessing.model;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static java.lang.Math.PI;

import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.route.ObjectType;
import org.locationtech.jts.geom.Coordinate;

public record MinimumBoundingRectangleEq(
    Coordinate centroid, ObjectType type, IntXY origin, double angle, double area) {

  public MinimumBoundingRectangle toMinimumBoundingRectangle(int width, int height) {
    var normalizedAngle = angle < PI / 2 ? PI / 2 + angle : angle;
    double dx = width / 2.0;
    double dy = height / 2.0;

    Coordinate[] corners =
        new Coordinate[] {
          new Coordinate(-dx, -dy),
          new Coordinate(dx, -dy),
          new Coordinate(dx, dy),
          new Coordinate(-dx, dy),
          new Coordinate(-dx, -dy)
        };

    for (Coordinate corner : corners) {
      double x = corner.x;
      double y = corner.y;

      double xr = x * Math.cos(normalizedAngle) - y * Math.sin(normalizedAngle);
      double yr = x * Math.sin(normalizedAngle) + y * Math.cos(normalizedAngle);

      corner.x = Math.round(centroid.x + xr);
      corner.y = Math.round(centroid.y + yr);
    }

    var polygon = geometryFactory.createPolygon(corners);
    var tile = new TiledPolygon(polygon, type, origin, TilingConf.getDefaultInstance());
    return new MinimumBoundingRectangle(tile);
  }
}
