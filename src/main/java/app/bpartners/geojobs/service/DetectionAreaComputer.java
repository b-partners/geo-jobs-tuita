package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionAreaComputer implements Function<Detection, Double> {
  private final GeometryConverter geometryConverter;
  private final GeometrySquareMeterArea geometrySquareMeterArea;

  @Override
  public Double apply(Detection detection) {
    var geoJsonZone = detection.getProvidedGeoJsonZone();
    return apply(geoJsonZone);
  }

  public Double apply(List<Feature> geoJsonZone) {
    var unifiedProvidedPolygon =
        geoJsonZone.stream()
            .map(
                feature -> {
                  var geometryType = feature.getGeometry().getActualInstance();
                  org.locationtech.jts.geom.MultiPolygon geometry;
                  switch (geometryType) {
                    // TODO: only used for BP_TOITURE model so must be fixed
                    case Point point ->
                        geometry = geometryConverter.retrieveNearestRoofMultiPolygon(point);
                    case Polygon polygon ->
                        geometry = geometryConverter.apply(List.of(polygon.getCoordinates()));
                    case MultiPolygon multiPolygon ->
                        geometry = geometryConverter.apply(multiPolygon.getCoordinates());
                    default ->
                        throw new UnsupportedOperationException(
                            "Unsupported geometry type for validation: " + geometryType);
                  }
                  return geometry;
                })
            .filter(Objects::nonNull)
            .reduce(unifyMultiPolygon())
            .orElse(null);
    if (unifiedProvidedPolygon == null) {
      throw new IllegalArgumentException("No unified geometry found from provided geo-json");
    }
    var providedZoneGeometries = unifiedProvidedPolygon.getNumGeometries();
    var accumulatedArea = 0.0;
    for (int i = 0; i < providedZoneGeometries; i++) {
      var providedPolygonGeometry = unifiedProvidedPolygon.getGeometryN(i);
      var computedArea = geometrySquareMeterArea.apply(providedPolygonGeometry);
      accumulatedArea += computedArea;
    }
    return accumulatedArea;
  }
}
