package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionZoneToProcessProvider implements Function<Detection, MultiPolygon> {
  private final GeometryConverter geometryConverter;

  @Override
  public MultiPolygon apply(Detection detection) {
    if (detection == null) {
      return geometryFactory.createMultiPolygon(new Polygon[0]);
    }
    var providedGeoJsonZone = detection.getProvidedGeoJsonZone();
    return apply(detection.getId(), providedGeoJsonZone);
  }

  private MultiPolygon apply(String detectionId, List<Feature> featureList) {
    if (featureList == null) {
      return geometryFactory.createMultiPolygon(new Polygon[0]);
    }
    if (featureList.isEmpty()) {
      return geometryFactory.createMultiPolygon(new Polygon[0]);
    }
    return featureList.stream()
        .map(
            feature -> {
              var geometry = feature.getGeometry();
              if (geometry == null) {
                return null;
              }
              var geometryType = geometry.getActualInstance();
              return switch (geometryType) {
                case Point point -> geometryConverter.retrieveNearestRoofMultiPolygon(point);
                case app.bpartners.geojobs.endpoint.rest.model.Polygon polygon ->
                    geometryConverter.apply(List.of(polygon.getCoordinates()));
                case app.bpartners.geojobs.endpoint.rest.model.MultiPolygon multiPolygon ->
                    geometryConverter.apply(multiPolygon.getCoordinates());
                default ->
                    throw new UnsupportedOperationException(
                        "Unsupported geometry type to retrieve zone to process : " + geometryType);
              };
            })
        .filter(Objects::nonNull)
        .reduce(unifyMultiPolygon())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unable to unify provided zone for detection.id : " + detectionId));
  }
}
