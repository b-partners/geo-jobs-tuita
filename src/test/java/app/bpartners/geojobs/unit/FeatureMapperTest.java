package app.bpartners.geojobs.unit;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.endpoint.rest.model.MultiPolygon.TypeEnum.MULTI_POLYGON;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

class FeatureMapperTest {
  private final String id = randomUUID().toString();
  private final FeatureMapper subject = new FeatureMapper(new GeometryConverter(null));

  private Feature expectedFeature() {
    Feature feature = new Feature();
    var coordinates =
        List.of(
            List.of(
                List.of(
                    List.of(
                        BigDecimal.valueOf(6.958009303660302),
                        BigDecimal.valueOf(43.543013820437459)),
                    List.of(
                        BigDecimal.valueOf(6.957965493371299),
                        BigDecimal.valueOf(43.543002082885863)),
                    List.of(
                        BigDecimal.valueOf(6.957822106008073),
                        BigDecimal.valueOf(43.543033084979541)),
                    List.of(
                        BigDecimal.valueOf(6.957796040201745),
                        BigDecimal.valueOf(43.543066366941567)),
                    List.of(
                        BigDecimal.valueOf(6.957877191721906),
                        BigDecimal.valueOf(43.543303862183095)),
                    List.of(
                        BigDecimal.valueOf(6.957988034043352),
                        BigDecimal.valueOf(43.54328420602328)),
                    List.of(
                        BigDecimal.valueOf(6.958082768541455),
                        BigDecimal.valueOf(43.543132354704881)),
                    List.of(
                        BigDecimal.valueOf(6.958009303660302),
                        BigDecimal.valueOf(43.543013820437459)))));
    MultiPolygon multiPolygon = new MultiPolygon().coordinates(coordinates);
    multiPolygon.setType(MULTI_POLYGON);
    feature.setGeometry(new FeatureGeometry(multiPolygon));
    feature.getProperties().put("zoom", 20);
    feature.getProperties().put("id", id);
    return feature;
  }

  private Polygon expectedPolygon() {
    var start = new Coordinate(6.958009303660302, 43.543013820437459);
    return new GeometryFactory()
        .createPolygon(
            new Coordinate[] {
              start,
              new Coordinate(6.957965493371299, 43.543002082885863),
              new Coordinate(6.957822106008073, 43.543033084979541),
              new Coordinate(6.957796040201745, 43.543066366941567),
              new Coordinate(6.957877191721906, 43.543303862183095),
              new Coordinate(6.957988034043352, 43.54328420602328),
              new Coordinate(6.958082768541455, 43.543132354704881),
              start,
            });
  }

  @Test
  void feature_to_geo_tools_polygon_mapper_ok() {
    Polygon polygon = subject.toDomainPolygon(expectedFeature());

    assertEquals(expectedPolygon(), polygon);
  }

  @Test
  void geo_tools_polygon_to_rest_feature_mapper_ok() {
    Feature feature = subject.toRest(expectedPolygon(), 20, id);

    assertEquals(expectedFeature(), feature);
  }

  @Test
  void feature_to_geo_tools_polygon_mapper_with_null_zoom_ok() {
    var polygon = subject.domainToJtsPolygon(toDomainFeature(expectedFeature()));

    assertEquals(expectedPolygon(), polygon);
  }
}
