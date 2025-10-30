package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE;
import static app.bpartners.geojobs.endpoint.rest.model.Polygon.TypeEnum.POLYGON;
import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.getRoofMultiPolygon;

import app.bpartners.geojobs.endpoint.event.model.FeatureVggRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.model.geometry.TiledPixelPolygon;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.detection.*;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.service.DetectionVGGUpdate;
import app.bpartners.geojobs.service.PolygonCoordinatesCloser;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureVggRequestedService implements Consumer<FeatureVggRequested> {
  private final DetectionRepository detectionRepository;
  private final MachineDetectedTileRepository detectedTileRepository;
  private final VGGFactory vggFactory;
  private final GeometryConverter geometryConverter;
  private final DetectionVGGUpdate detectionVGGUpdate;
  private final PolygonCoordinatesCloser polygonCoordinatesCloser;
  private final TileCoordinatesPolygonIntersection tileCoordinatesPolygonIntersection;
  private final FeatureMapper featureMapper;
  private final DetectionRoofPropertiesRequestedService detectionRoofPropertiesRequestedService;
  private final TileFinder tileFinder;

  @Override
  public void accept(FeatureVggRequested event) {
    var detectionIdentifier = event.getDetectionIdentifier();
    var feature = event.getFeature();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    if (!detection.hasToitureModelName()) {
      log.error("Only BP_TOITURE model is supported to generated VGG from now");
      return;
    }
    var machineDetectedTiles = detectedTileRepository.findAllByZdjJobId(detection.getZdjId());
    var featureDelimitationWithRoofProperties =
        detectionRoofPropertiesRequestedService.applyRoofPropertiesOnDelimitation(
            machineDetectedTiles,
            detection.getFeatureWithDelimitations().stream()
                .filter(
                    f ->
                        f.getRestFeature() != null
                            && f.getRestFeature().getGeometry() != null
                            && f.getRestFeature().getGeometry().equals(feature.getGeometry()))
                .findFirst()
                .orElseThrow());
    var polygonGeoJson = getPolygonGeoJsonFromFeature(feature);
    if (polygonGeoJson == null) return;
    var detectableTypes =
        detection.getDetectableObjectConfigurations().stream()
            .map(DetectableObjectConfiguration::getObjectType)
            .toList();
    var latLonRoofFeatures = featureDelimitationWithRoofProperties.getRestDelimitations();
    var tiledPixelPolygons =
        getTiledPixelPolygon(
            polygonGeoJson, latLonRoofFeatures, detectableTypes, machineDetectedTiles);
    var featureTileCoordinates = retrieveFeatureTileCoordinates(feature);

    var vggMap = vggFactory.from(tiledPixelPolygons, featureTileCoordinates);

    var newDetection = detectionVGGUpdate.apply(vggMap.values(), detection, event.getFeatureNb());

    detectionRepository.save(newDetection);
  }

  private Feature getPolygonGeoJsonFromFeature(Feature feature) {
    Feature polygonGeoJsonZone;
    var geometryInstance = feature.getGeometry().getActualInstance();
    switch (geometryInstance) {
      case Point point -> {
        var roofMultiPolygonCoordinates =
            geometryConverter.multiPolygonToNestedList(
                geometryConverter.retrieveNearestRoofMultiPolygon(point));
        if (roofMultiPolygonCoordinates.size() > 1) {
          log.error(
              "MultiPolygon roof with more than one polygon is not supported, only the first one is"
                  + " taken");
        }
        polygonGeoJsonZone =
            new Feature()
                .type(FEATURE)
                .properties(feature.getProperties())
                .geometry(
                    new FeatureGeometry(
                        new app.bpartners.geojobs.endpoint.rest.model.Polygon()
                            .type(POLYGON)
                            .coordinates(roofMultiPolygonCoordinates.getFirst())));
      }
      case app.bpartners.geojobs.endpoint.rest.model.Polygon ignored -> {
        polygonGeoJsonZone = feature;
      }
      case MultiPolygon multiPolygon -> {
        if (multiPolygon.getCoordinates().size() > 1) {
          log.error(
              "Provided multiPolygon with more than one polygon is not supported, only the first"
                  + " one is taken");
        }
        polygonGeoJsonZone =
            new Feature()
                .type(FEATURE)
                .properties(feature.getProperties())
                .geometry(
                    new FeatureGeometry(
                        new app.bpartners.geojobs.endpoint.rest.model.Polygon()
                            .type(POLYGON)
                            .coordinates(multiPolygon.getCoordinates().getFirst())));
      }
      default -> {
        log.error(
            "Unexpected geometry type: {} aborting vgg computing for feature {}",
            geometryInstance,
            feature);
        return null;
      }
    }
    return polygonGeoJsonZone;
  }

  private List<TileCoordinates> retrieveFeatureTileCoordinates(Feature feature) {
    var polygonGeometry = geometryConverter.retrievePolygonGeometry(feature);
    return tileFinder.getFromGeoJsonPolygon(polygonGeometry, HOUSES_0.getZoomLevel()).stream()
        .sorted(
            Comparator.comparing(TileCoordinates::getZ)
                .thenComparing(TileCoordinates::getY)
                .thenComparing(TileCoordinates::getX))
        .toList();
  }

  private List<List<List<List<BigDecimal>>>> getRestMultipolygonData(
      app.bpartners.geojobs.repository.model.Feature feature) {
    var restFeature = toRestFeature(feature);
    var jtsGeometry = featureMapper.toDomainGeometry(restFeature);

    if (jtsGeometry instanceof Polygon) {
      return List.of(restFeature.getGeometry().getPolygon().getCoordinates());
    }

    return restFeature.getGeometry().getMultiPolygon().getCoordinates();
  }

  private List<TiledPixelPolygon> getTiledPixelPolygon(
      Feature polygonGeoJsonZone,
      List<Feature> latLonRoofFeatures,
      List<DetectableType> detectableTypes,
      List<MachineDetectedTile> detectedTileList) {
    var providedLatLonPolygonGeometry =
        geometryConverter.apply(
            List.of(polygonGeoJsonZone.getGeometry().getPolygon().getCoordinates()));

    return latLonRoofFeatures.stream()
        .map(
            roofFeature -> {
              var roofGeometry = getRoofMultiPolygon(roofFeature);
              return detectedTileList.stream()
                  .map(
                      detectedTile -> {
                        var tileCoordinates = detectedTile.getTile().getCoordinates();
                        var providedZoneInsideTileGeometry =
                            tileCoordinatesPolygonIntersection.intersection(
                                providedLatLonPolygonGeometry, tileCoordinates);
                        var providedZoneAndRoofInsideTileGeometry =
                            providedZoneInsideTileGeometry.intersection(roofGeometry);
                        var providedZoneAndRoofInsideTilePolygonCoordinates =
                            tileCoordinatesPolygonIntersection.intersects(
                                providedZoneAndRoofInsideTileGeometry, tileCoordinates);
                        if (providedZoneAndRoofInsideTilePolygonCoordinates.isEmpty()) {
                          return null;
                        }
                        var providedZoneAndRoofInsideTilePixelGeometry =
                            geometryConverter.convertToPolygon(
                                providedZoneAndRoofInsideTilePolygonCoordinates);
                        var polygonObjectTypes =
                            detectedTile.getDetectedObjects().stream()
                                .map(
                                    detectedObject -> {
                                      var detectableType =
                                          detectedObject
                                              .getDetectedObjectType()
                                              .getDetectableType();
                                      if (!detectableTypes.contains(detectableType)) {
                                        return null;
                                      }
                                      var polygonCoordinates =
                                          detectedObject
                                              .getFeature()
                                              .getGeometry()
                                              .getMultiPolygon()
                                              .getCoordinates()
                                              .getFirst()
                                              .getFirst();
                                      var closedPolygon =
                                          polygonCoordinatesCloser.apply(polygonCoordinates);
                                      var detectedObjectPolygonPixel =
                                          geometryConverter.toPolygon(
                                              List.of(List.of(closedPolygon)));
                                      var intersectionBetweenDetectedObjectAndProvidedZone =
                                          detectedObjectPolygonPixel
                                              .intersection(
                                                  providedZoneAndRoofInsideTilePixelGeometry)
                                              .buffer(0);
                                      if (intersectionBetweenDetectedObjectAndProvidedZone
                                          instanceof Polygon polygon) {
                                        return new PolygonObjectType(
                                            polygon, detectedObject.getDetectableObjectType());
                                      }
                                      return null;
                                    })
                                .filter(Objects::nonNull)
                                .toList();
                        return new TiledPixelPolygon(
                            roofFeature,
                            polygonObjectTypes,
                            tileCoordinates.getX(),
                            tileCoordinates.getY(),
                            tileCoordinates.getZ());
                      })
                  .toList();
            })
        .flatMap(List::stream)
        .filter(Objects::nonNull)
        .toList();
  }
}
