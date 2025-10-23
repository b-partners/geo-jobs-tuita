package app.bpartners.geojobs.unit;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.RoofDelimiterMapper;
import app.bpartners.geojobs.endpoint.rest.model.RoofDelimiter;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

class RoofDelimiterMapperTest {
  private final FeatureMapper featureMapper = new FeatureMapper(new GeometryConverter(null));
  private final RoofDelimiterMapper subject = new RoofDelimiterMapper(featureMapper);

  private RoofDelimiter sampleRoofDelimiter() {
    var coords =
        List.of(
            List.of(BigDecimal.valueOf(1.0), BigDecimal.valueOf(2.0)),
            List.of(BigDecimal.valueOf(2.0), BigDecimal.valueOf(3.0)),
            List.of(BigDecimal.valueOf(3.0), BigDecimal.valueOf(1.0)),
            List.of(BigDecimal.valueOf(1.0), BigDecimal.valueOf(2.0)));

    RoofDelimiter rd = new RoofDelimiter();
    rd.setPolygon(coords);
    return rd;
  }

  private Polygon expectedPolygon() {
    return geometryFactory.createPolygon(
        new Coordinate[] {
          new Coordinate(1.0, 2.0),
          new Coordinate(2.0, 3.0),
          new Coordinate(3.0, 1.0),
          new Coordinate(1.0, 2.0)
        });
  }

  @Test
  void toJtsPolygon_ok() {
    var polygon = subject.toJtsPolygon(sampleRoofDelimiter());

    assertEquals(expectedPolygon(), polygon);
  }

  @Test
  void toJtsPolygon_with_null_polygon_throws() {
    var roofDelimiter = new RoofDelimiter();
    roofDelimiter.setPolygon(null);

    assertThrows(BadRequestException.class, () -> subject.toJtsPolygon(roofDelimiter));
  }

  @Test
  void toDomainFeature_ok() {
    var roofDelimiter = sampleRoofDelimiter();

    var feature = subject.toDomainFeature(roofDelimiter);

    var polygon = subject.toJtsPolygon(roofDelimiter);
    var polygonFromFeature =
        featureMapper.toDomainPolygon(featureMapper.toRest(polygon, 20, feature.getId()));

    assertEquals(polygon, polygonFromFeature);
  }

  @Test
  void domainFeature_toRestPolygon_ok() {
    var roofDelimiter = subject.toDomainFeature(sampleRoofDelimiter());
    var expected = sampleRoofDelimiter().getPolygon();

    var actual = subject.toRestPolygon(roofDelimiter);

    assertEquals(expected, actual);
  }
}
