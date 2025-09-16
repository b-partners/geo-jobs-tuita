package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;

import app.bpartners.geojobs.endpoint.event.model.ZoneVggRequested;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.model.geometry.TiledPixelPolygon;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.DetectionVGGUpdate;
import app.bpartners.geojobs.service.PolygonCoordinatesCloser;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneVggRequestedService implements Consumer<ZoneVggRequested> {
  private final DetectionRepository detectionRepository;
  private final MachineDetectedTileRepository detectedTileRepository;
  private final VGGFactory vggFactory;
  private final GeometryConverter geometryConverter;
  private final TilingTaskRepository tilingTaskRepository;
  private final DetectionVGGUpdate detectionVGGUpdate;
  private final PolygonCoordinatesCloser polygonCoordinatesCloser;
  private final TileCoordinatesPolygonIntersection tileCoordinatesPolygonIntersection;

  @Override
  public void accept(ZoneVggRequested event) {
    var detectionIdentifier = event.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    var zoneDetectionJobIdentifier = detection.getZdjId();
    var zoneTilingJobIdentifier = detection.getZtjId();
    var detectableTypes =
        detection.getDetectableObjectConfigurations().stream()
            .map(DetectableObjectConfiguration::getObjectType)
            .toList();
    var providedPolygonZone = detection.getPolygonGeoJsonZone();

    var latLonRoofMultiPolygon = retrieveLatLonRoofMultiPolygon(detection, providedPolygonZone);
    var latLonRoofInsideProvidedZone =
        computeLatLonRoofIntersectionWithProvided(latLonRoofMultiPolygon, providedPolygonZone);
    var tileCoordinates = retrieveTileCoordinates(zoneTilingJobIdentifier);
    var tiledPixelPolygons =
        getTiledPixelPolygon(zoneDetectionJobIdentifier, providedPolygonZone, detectableTypes);

    var vggMap = vggFactory.from(tiledPixelPolygons, latLonRoofInsideProvidedZone, tileCoordinates);

    var newDetection = detectionVGGUpdate.apply(vggMap, detection);

    detectionRepository.save(newDetection);
  }

  private MultiPolygon computeLatLonRoofIntersectionWithProvided(
      MultiPolygon latLonRoofMultiPolygon, Feature providedPolygonZone) {
    var providedZoneGeometry =
        geometryConverter.apply(
            List.of(providedPolygonZone.getGeometry().getPolygon().getCoordinates()));
    var latLonRoofInsideProvidedZone = latLonRoofMultiPolygon.intersection(providedZoneGeometry);
    if (latLonRoofInsideProvidedZone instanceof MultiPolygon multiPolygon) {
      return multiPolygon;
    }
    if (latLonRoofInsideProvidedZone instanceof Polygon polygon) {
      return geometryFactory.createMultiPolygon(new Polygon[] {polygon});
    }
    throw new IllegalStateException(
        "Unable to convert latLonRoofInsideProvidedZone to MultiPolygon : "
            + geometryConverter.writeGeometryAsString(latLonRoofMultiPolygon));
  }

  private List<TileCoordinates> retrieveTileCoordinates(String zoneTilingJobIdentifier) {
    var tilingTasks = tilingTaskRepository.findAllByJobId(zoneTilingJobIdentifier);
    return tilingTasks.stream()
        .map(
            parcelTilingTask ->
                parcelTilingTask.getTiles().stream().map(Tile::getCoordinates).toList())
        .flatMap(List::stream)
        .sorted(
            Comparator.comparing(TileCoordinates::getZ)
                .thenComparing(TileCoordinates::getY)
                .thenComparing(TileCoordinates::getX))
        .toList();
  }

  private MultiPolygon retrieveLatLonRoofMultiPolygon(
      Detection detection, Feature polygonGeoJsonZone) {
    return detection.getFeatureWithDelimitations().stream()
        .filter(
            featureWithDelimitation ->
                toRestFeature(featureWithDelimitation.feature()).equals(polygonGeoJsonZone))
        .map(
            featureWithDelimitation ->
                featureWithDelimitation.delimitations().stream()
                    .map(
                        feature ->
                            geometryConverter.apply(
                                toRestFeature(feature)
                                    .getGeometry()
                                    .getMultiPolygon()
                                    .getCoordinates()))
                    .toList())
        .flatMap(List::stream)
        .reduce(unifyMultiPolygon())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to unify roof multiPolygon to request zone VGG for detection.id="
                        + detection.getId()));
  }

  private List<TiledPixelPolygon> getTiledPixelPolygon(
      String zoneDetectionJobIdentifier,
      Feature polygonGeoJsonZone,
      List<DetectableType> detectableTypes) {
    var providedLatLonPolygonGeometry =
        geometryConverter.apply(
            List.of(polygonGeoJsonZone.getGeometry().getPolygon().getCoordinates()));
    var detectedTileList = detectedTileRepository.findAllByZdjJobId(zoneDetectionJobIdentifier);
    return detectedTileList.stream()
        .map(
            detectedTile -> {
              var tileCoordinates = detectedTile.getTile().getCoordinates();
              var intersectionBetweenDetectedTileAndProvidedZone =
                  tileCoordinatesPolygonIntersection.intersects(
                      providedLatLonPolygonGeometry, tileCoordinates);
              if (intersectionBetweenDetectedTileAndProvidedZone.isEmpty()) {
                return null;
              }
              var providedZoneInsideTilePixel =
                  geometryConverter.convertToPolygon(
                      intersectionBetweenDetectedTileAndProvidedZone);
              var polygonObjectTypes =
                  detectedTile.getDetectedObjects().stream()
                      .map(
                          detectedObject -> {
                            var detectableType =
                                detectedObject.getDetectedObjectType().getDetectableType();
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
                            var closedPolygon = polygonCoordinatesCloser.apply(polygonCoordinates);
                            var detectedObjectPolygonPixel =
                                geometryConverter.toPolygon(List.of(List.of(closedPolygon)));
                            var intersectionBetweenDetectedObjectAndProvidedZone =
                                detectedObjectPolygonPixel.intersection(
                                    providedZoneInsideTilePixel);
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
                  polygonGeoJsonZone,
                  polygonObjectTypes,
                  tileCoordinates.getX(),
                  tileCoordinates.getY(),
                  tileCoordinates.getZ());
            })
        .toList();
  }
}
