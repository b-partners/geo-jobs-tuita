package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.math.RoundingMode.UP;

import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.model.exception.UnsupportedDetectionAreaException;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionSupportedAreaValidator implements Consumer<Detection> {
  private static final double ONE_KILOMETER_SQUARE_METER = 1_000_000.0;
  private final GeometrySquareMeterArea geometrySquareMeterArea;
  private final GeometryConverter geometryConverter;

  @Override
  public void accept(Detection detection) {
    var geoJsonZone = detection.getProvidedGeoJsonZone();
    if (geoJsonZone == null || geoJsonZone.isEmpty()) {
      return;
    }
    var unifiedProvidedPolygon =
        geoJsonZone.stream()
            .map(
                feature -> {
                  var geometryType = feature.getGeometry().getActualInstance();
                  org.locationtech.jts.geom.MultiPolygon geometry;
                  switch (geometryType) {
                    case Point ignored -> geometry = null;
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
      log.warn("No unified geometry found from provided geo-json: {}", geoJsonZone);
      return;
    }
    var providedZoneGeometries = unifiedProvidedPolygon.getNumGeometries();
    var accumulatedArea = 0.0;
    for (int i = 0; i < providedZoneGeometries; i++) {
      var providedPolygonGeometry = unifiedProvidedPolygon.getGeometryN(i);
      var computedArea = geometrySquareMeterArea.apply(providedPolygonGeometry);
      log.info("computed area {} m^2", Math.round(computedArea));
      if (computedArea > ONE_KILOMETER_SQUARE_METER) {
        throwUnsupportedDetectionAreaException(providedPolygonGeometry, computedArea);
      }
      accumulatedArea += computedArea;
    }
    if (accumulatedArea > ONE_KILOMETER_SQUARE_METER) {
      throwUnsupportedDetectionAreaException(unifiedProvidedPolygon, accumulatedArea);
    }
  }

  private void throwUnsupportedDetectionAreaException(
      Geometry providedPolygonGeometry, Double computedArea) {
    throw new UnsupportedDetectionAreaException(
        "Provided zone contains geometry "
            + providedPolygonGeometry.toText()
            + " over 1 kilometer square degree area :"
            + BigDecimal.valueOf(computedArea).setScale(0, UP).longValue()
            + " m^2",
        computedArea);
  }
}
