package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_DEGREE;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

class GeometryAreaConverterTest {
  private static final GeometrySquareMeterArea geometrySquareMeterArea =
      new GeometrySquareMeterArea();
  private static final GeometryAreaConverter subject =
      new GeometryAreaConverter(geometrySquareMeterArea);

  @Test
  void from_square_degree_to_square_meter() {
    var polygon = randomWGS84Polygon();
    var projectedPolygon = geometrySquareMeterArea.project(polygon, WGS84, LAMBERT_93);
    var expected = projectedPolygon.getArea();
    var actual = subject.apply(polygon.getArea(), SQUARE_DEGREE, SQUARE_METER);

    assertEquals(expected, actual, 1e-3);
  }

  @Test
  void from_square_meter_to_square_degree() {
    var polygon = randomWGS84Polygon();
    var projectedPolygon = geometrySquareMeterArea.project(polygon, WGS84, LAMBERT_93);

    var expected = polygon.getArea();
    var actual = subject.apply(projectedPolygon.getArea(), SQUARE_METER, SQUARE_DEGREE);

    assertEquals(expected, actual, 1e-12);
  }

  @Test
  void same_unit() {
    var polygon = randomWGS84Polygon();
    var expected = polygon.getArea();

    var actual = subject.apply(polygon.getArea(), SQUARE_DEGREE, SQUARE_DEGREE);

    assertEquals(expected, actual, 1e-12);
  }

  private static Polygon randomWGS84Polygon() {
    var coordinates =
        new Coordinate[] {
          new Coordinate(2.3487004023124882, 48.84755491826553),
          new Coordinate(2.348633102506028, 48.84752574466032),
          new Coordinate(2.3486833102980142, 48.84750184338105),
          new Coordinate(2.3487324498386215, 48.8475341804033),
          new Coordinate(2.3487004023124882, 48.84755491826553)
        };
    return geometryFactory.createPolygon(coordinates);
  }
}
