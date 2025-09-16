package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.RoofDelimiter;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.repository.model.Feature;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoofDelimiterMapper {
  private final FeatureMapper featureMapper;

  public Feature toDomainFeature(RoofDelimiter rest) {
    var jtsPolygon = toJtsPolygon(rest);
    var restFeature =
        featureMapper.toRest(jtsPolygon, HOUSES_0.getZoomLevel(), randomUUID().toString());

    return FeatureMapper.toDomainFeature(restFeature);
  }

  public Polygon toJtsPolygon(RoofDelimiter rest) {
    var polygon = rest.getPolygon();

    if (polygon == null) {
      throw new BadRequestException("Polygon data is required and cannot be null.");
    }

    var coordinates =
        rest.getPolygon().stream()
            .map(p -> new Coordinate(p.getFirst().doubleValue(), p.get(1).doubleValue()))
            .toArray(Coordinate[]::new);

    return geometryFactory.createPolygon(coordinates);
  }

  public List<List<BigDecimal>> toRestPolygon(Feature feature) {
    var jtsPolygon = featureMapper.domainToJtsPolygon(feature);
    return Arrays.stream(jtsPolygon.getCoordinates())
        .map(
            coordinate ->
                List.of(
                    BigDecimal.valueOf(coordinate.getX()), BigDecimal.valueOf(coordinate.getY())))
        .toList();
  }
}
