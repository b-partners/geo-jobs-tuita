package app.bpartners.geojobs.service.lidar.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeometryWithPropertiesTest {
  @Test
  void as_polygon_should_throw_if_point_is_provided() {
    var point = point();
    var subject = new GeometryWithProperties(point, Map.of());

    assertThrows(IllegalArgumentException.class, subject::asPolygon);
  }

  @Test
  void as_polygon_should_throws_if_multipolygon_is_provided_without_geometries() {
    var emptyMultipolygon = multiPolygon(new Polygon[] {});
    var subject = new GeometryWithProperties(emptyMultipolygon, Map.of());
    assertThrows(IllegalArgumentException.class, subject::asPolygon);
  }

  @Test
  void get_first_element_of_multipolygon_if_valid() {
    var expected = polygon();
    var emptyMultipolygon = multiPolygon(new Polygon[] {expected});
    var subject = new GeometryWithProperties(emptyMultipolygon, Map.of());

    assertEquals(expected, subject.asPolygon());
  }

  private static Point point() {
    return geometryFactory.createPoint(new Coordinate(0, 1));
  }

  private static Polygon polygon() {
    var coordinates =
        new Coordinate[] {
          new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(1, 2), new Coordinate(0, 1)
        };
    return geometryFactory.createPolygon(coordinates);
  }

  private static MultiPolygon multiPolygon(Polygon[] polygons) {
    return geometryFactory.createMultiPolygon(polygons);
  }
}
