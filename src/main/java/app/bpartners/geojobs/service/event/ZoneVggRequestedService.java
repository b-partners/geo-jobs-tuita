package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.getRoofMultiPolygon;

import app.bpartners.geojobs.endpoint.event.model.ZoneVggRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.model.geometry.TiledPixelPolygon;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.detection.*;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.DetectionVGGUpdate;
import app.bpartners.geojobs.service.PolygonCoordinatesCloser;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
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
public class ZoneVggRequestedService implements Consumer<ZoneVggRequested> {
  private final DetectionRepository detectionRepository;
  private final MachineDetectedTileRepository detectedTileRepository;
  private final VGGFactory vggFactory;
  private final GeometryConverter geometryConverter;
  private final TilingTaskRepository tilingTaskRepository;
  private final DetectionVGGUpdate detectionVGGUpdate;
  private final PolygonCoordinatesCloser polygonCoordinatesCloser;
  private final TileCoordinatesPolygonIntersection tileCoordinatesPolygonIntersection;
  private final FeatureMapper featureMapper;
  private final DetectionRoofPropertiesRequestedService detectionRoofPropertiesRequestedService;

  @Override
  public void accept(ZoneVggRequested event) {
    var detectionIdentifier = event.getDetectionIdentifier();
    var retrievedDetection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    var machineDetectedTiles =
        detectedTileRepository.findAllByZdjJobId(retrievedDetection.getZdjId());
    var detectionWithRoofProperties =
        detectionRoofPropertiesRequestedService.apply(retrievedDetection, machineDetectedTiles);
    var polygonGeoJsonZone = detectionWithRoofProperties.getPolygonGeoJsonZone();
    var detectableTypes =
        detectionWithRoofProperties.getDetectableObjectConfigurations().stream()
            .map(DetectableObjectConfiguration::getObjectType)
            .toList();
    var latLonRoofFeatures =
        detectionWithRoofProperties.getFeatureWithDelimitations().stream()
            .map(FeatureWithDelimitation::getRestDelimitations)
            .flatMap(features -> features != null ? features.stream() : null)
            .filter(Objects::nonNull)
            .toList();
    var tileCoordinates = retrieveTileCoordinates(detectionWithRoofProperties);
    var tiledPixelPolygons =
        getTiledPixelPolygon(
            polygonGeoJsonZone, latLonRoofFeatures, detectableTypes, machineDetectedTiles);

    var vggMap = vggFactory.from(tiledPixelPolygons, tileCoordinates);

    var newDetection = detectionVGGUpdate.apply(vggMap.values(), detectionWithRoofProperties);

    detectionRepository.save(newDetection);
  }

  private List<TileCoordinates> retrieveTileCoordinates(Detection detection) {
    var tilingTasks = tilingTaskRepository.findAllByJobId(detection.getZtjId());
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
