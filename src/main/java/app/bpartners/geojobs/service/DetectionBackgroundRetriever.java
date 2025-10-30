package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;

import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionBackgroundRetriever implements Function<Detection, MultiPolygon> {
  private final DetectionZoneToProcessProvider detectionProvidedZoneUnifier;
  private final GeometryConverter geometryConverter;

  @Override
  public MultiPolygon apply(Detection detection) {
    if (!detection.hasToitureModelName()) {
      throw new NotImplementedException(
          "Only detection using TOITURE model is supported for now, otherwise actual is "
              + detection.getDetectableObjectModel().getModelName());
    }
    var unifiedProvidedZone = detectionProvidedZoneUnifier.apply(detection);
    var roofMultipolygon =
        detection.getFeatureWithDelimitations().stream()
            .map(FeatureWithDelimitation::delimitations)
            .flatMap(List::stream)
            .map(
                feature -> {
                  var restFeature = toRestFeature(feature);
                  var geometryType = restFeature.getGeometry().getActualInstance();
                  switch (geometryType) {
                    case Polygon polygon -> {
                      return geometryConverter.apply(List.of(polygon.getCoordinates()));
                    }
                    case app.bpartners.geojobs.endpoint.rest.model.MultiPolygon multiPolygon -> {
                      return geometryConverter.apply(multiPolygon.getCoordinates());
                    }
                    default ->
                        throw new IllegalStateException(
                            "Unexpected geometry type to retrieve detection background: "
                                + geometryType);
                  }
                })
            .reduce(unifyMultiPolygon())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to unify roof multipolygons for detection: " + detection.getId()));
    var intersectionBetweenProvidedZoneAndUnifiedRoofsMultiPolygon =
        unifiedProvidedZone.isEmpty()
            ? roofMultipolygon
            : unifiedProvidedZone.intersection(roofMultipolygon);
    var providedZoneWithoutRoofs =
        unifiedProvidedZone.isEmpty()
            ? intersectionBetweenProvidedZoneAndUnifiedRoofsMultiPolygon
            : unifiedProvidedZone.difference(
                intersectionBetweenProvidedZoneAndUnifiedRoofsMultiPolygon);
    switch (providedZoneWithoutRoofs) {
      case org.locationtech.jts.geom.Polygon polygon -> {
        return geometryFactory.createMultiPolygon(
            new org.locationtech.jts.geom.Polygon[] {polygon});
      }
      case MultiPolygon multiPolygon -> {
        return multiPolygon;
      }
      default ->
          throw new IllegalStateException(
              "Unexpected geometry type to retrieve detection background: "
                  + providedZoneWithoutRoofs);
    }
  }
}
