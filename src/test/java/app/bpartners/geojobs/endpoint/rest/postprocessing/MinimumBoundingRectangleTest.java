package app.bpartners.geojobs.endpoint.rest.postprocessing;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.tombe;
import static java.awt.Color.BLACK;
import static java.lang.Math.PI;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.endpoint.rest.postprocessing.model.MinimumBoundingRectangle;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.MinimumBoundingRectangleEq;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.plot.PlotablePlane;
import app.bpartners.geojobs.model.geometry.polygon.PolygonOrientation;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

public class MinimumBoundingRectangleTest {
  public MinimumBoundingRectangle degrees45() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(200, 450),
          new Coordinate(50, 300),
          new Coordinate(200, 150),
          new Coordinate(350, 300),
          new Coordinate(200, 450)
        };

    var polygon = geometryFactory.createPolygon(coords);
    return new MinimumBoundingRectangle(
        new TiledPolygon(polygon, tombe, new IntXY(0, 0), TilingConf.getDefaultInstance()));
  }

  public MinimumBoundingRectangle degrees90() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(200, 200),
          new Coordinate(200, 400),
          new Coordinate(100, 400),
          new Coordinate(100, 200),
          new Coordinate(200, 200)
        };

    var polygon = geometryFactory.createPolygon(coords);
    return new MinimumBoundingRectangle(
        new TiledPolygon(polygon, tombe, new IntXY(0, 0), TilingConf.getDefaultInstance()));
  }

  public MinimumBoundingRectangle degrees300() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(200, 200),
          new Coordinate(300, 150),
          new Coordinate(400, 250),
          new Coordinate(300, 300),
          new Coordinate(200, 200)
        };

    var polygon = geometryFactory.createPolygon(coords);
    return new MinimumBoundingRectangle(
        new TiledPolygon(polygon, tombe, new IntXY(0, 0), TilingConf.getDefaultInstance()));
  }

  @Test
  void transform_45_to_eq() {
    var angle = new PolygonOrientation(degrees45().polygon()).angle();
    var fromEq =
        new MinimumBoundingRectangleEq(degrees45().getCenter(), tombe, new IntXY(0, 0), angle, 2500)
            .toMinimumBoundingRectangle(degrees45().getWidth(), degrees45().getHeight());

    new PlotablePlane(1024, 1024).plot(Set.of(degrees45().polygon()), BLACK);
    assertEquals(PI / 4, angle);
    assertEquals(degrees45(), fromEq);
  }

  @Test
  void transform_90_to_eq() {
    var angle = new PolygonOrientation(degrees90().polygon()).get();
    var fromEq =
        new MinimumBoundingRectangleEq(degrees90().getCenter(), tombe, new IntXY(0, 0), angle, 2500)
            .toMinimumBoundingRectangle(degrees90().getWidth(), degrees90().getHeight());

    new PlotablePlane(1024, 1024).plot(Set.of(degrees90().polygon()), BLACK);
    new PlotablePlane(1024, 1024).plot(Set.of(fromEq.polygon()), BLACK);
    assertEquals(PI / 2, angle);
  }

  @Test
  void transform_300_to_eq() {
    var angle = new PolygonOrientation(degrees300().polygon()).angle();
    var fromEq =
        new MinimumBoundingRectangleEq(
                degrees300().getCenter(), tombe, new IntXY(0, 0), angle, 2500)
            .toMinimumBoundingRectangle(degrees300().getWidth(), degrees300().getHeight());

    new PlotablePlane(1024, 1024).plot(Set.of(degrees300().polygon()), BLACK);
    new PlotablePlane(1024, 1024).plot(Set.of(fromEq.polygon()), BLACK);
    assertEquals(-PI / 4, angle);
  }
}
