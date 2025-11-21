package app.bpartners.geojobs.service.lidar.model.geometry.roof;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class RoofPropertiesTest {
  private Polygon createPolygon(double x, double y) {
    var coordinates =
        new Coordinate[] {
          new Coordinate(x, y),
          new Coordinate(x + 1, y),
          new Coordinate(x + 1, y + 1),
          new Coordinate(x, y + 1),
          new Coordinate(x, y)
        };
    return geometryFactory.createPolygon(coordinates);
  }

  @Test
  void toPolygon_returns_same_polygon_instance_if_polygon() throws Exception {
    var expected = createPolygon(0, 0);

    var actual = invokeToPolygon(expected);

    assertEquals(expected, actual);
  }

  @Test
  void toPolygon_warns_and_returns_first_geometry_if_multiPolygon() throws Exception {
    var polygon1 = createPolygon(0, 0);
    var polygon2 = createPolygon(10, 10);
    var multiPolygon = geometryFactory.createMultiPolygon(new Polygon[] {polygon1, polygon2});

    var actual = invokeToPolygon(multiPolygon);

    assertEquals(polygon1, actual);
  }

  @Test
  void toPolygon_throws_exception_if_not_polygon_type() {
    var point = geometryFactory.createPoint(new Coordinate(0, 0));

    assertThrows(Exception.class, () -> invokeToPolygon(point));
  }

  private Polygon invokeToPolygon(Geometry geometry) throws Exception {
    var method = RoofProperties.class.getDeclaredMethod("toPolygon", Geometry.class);
    method.setAccessible(true);
    return (Polygon) method.invoke(null, geometry);
  }
}
