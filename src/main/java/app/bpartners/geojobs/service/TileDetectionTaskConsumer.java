package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.time.Instant.now;

import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.TileDetectionTask;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.repository.model.detection.MachineDetectedTile;
import app.bpartners.geojobs.service.detection.DetectionMapper;
import app.bpartners.geojobs.service.detection.DetectionResponse;
import app.bpartners.geojobs.service.detection.TileObjectDetector;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class TileDetectionTaskConsumer implements TaskConsumer<TileDetectionTask> {
  private final MachineDetectedTileRepository machineDetectedTileRepository;
  private final TileObjectDetector objectsDetector;
  private final DetectionMapper detectionMapper;
  private final DetectionRepository detectionRepository;
  private final GeometryConverter geometryConverter;
  private final DetectionMaskFromTileRetriever maskRetriever;

  @Override
  public void accept(TileDetectionTask tileDetectionTask) {
    var detectableObjectConfigurations = tileDetectionTask.getDetectableObjectConfigurations();
    var zoneDetectionJobId = tileDetectionTask.getZoneDetectionJobId();
    var parcelJobId = tileDetectionTask.getJobId();
    var address = tileDetectionTask.getAddress();
    var point = tileDetectionTask.getPoint();
    var tile = tileDetectionTask.getTile();
    File mask = null;
    var tileCoordinates = tile.getCoordinates();
    var detection = detectionRepository.findByZdjId(zoneDetectionJobId).orElse(null);
    if (detection != null) {
      if (detection.hasToitureModelName()) {
        var multiPolygonFromTile =
            geometryConverter.getMultiPolygonFromTile(
                tileCoordinates.getX(), tileCoordinates.getY(), tileCoordinates.getZ());
        var featureWithDelimitations = detection.getFeatureWithDelimitations();
        var roofMultiPolygonIntersectedWithTilePolygon =
            featureWithDelimitations.stream()
                .map(FeatureWithDelimitation::delimitations)
                .flatMap(List::stream)
                .map(
                    roofFeature -> {
                      var geometry =
                          geometryConverter.readGeometryFromString(
                              roofFeature.getGeometry().getActualInstanceStringValue());
                      if (geometry instanceof MultiPolygon roofMultiPolygon) {
                        return multiPolygonFromTile.intersection(roofMultiPolygon);
                      }
                      if (geometry instanceof Polygon roofPolygon) {
                        return multiPolygonFromTile.intersection(roofPolygon);
                      }
                      return null;
                    })
                .map(
                    geometry -> {
                      if (geometry instanceof MultiPolygon multiPolygon) {
                        return multiPolygon;
                      }
                      if (geometry instanceof Polygon polygon) {
                        return geometryFactory.createMultiPolygon(new Polygon[] {polygon});
                      }
                      return null;
                    })
                .filter(Objects::nonNull)
                .toList();
        if (!roofMultiPolygonIntersectedWithTilePolygon.isEmpty()) {
          var maskMultiPolygon =
              roofMultiPolygonIntersectedWithTilePolygon.stream()
                  //                      .map(roofMultiPolygon -> {
                  //                          var geometry =
                  // multiPolygonFromTile.difference(roofMultiPolygon);
                  //                          if( geometry instanceof MultiPolygon multiPolygon ) {
                  //                              return multiPolygon;
                  //                          }
                  //                          if( geometry instanceof Polygon polygon ) {
                  //                              return geometryFactory.createMultiPolygon(new
                  // Polygon[] {polygon});
                  //                          }
                  //                          return null;
                  //                      })
                  //                  .filter(Objects::nonNull)
                  .reduce(unifyMultiPolygon())
                  .orElse(null);
          mask = maskRetriever.apply(tile, maskMultiPolygon);
        } else {
          log.info(
              "Actual multiPolygon retrieved from tile {} not intersecting with any roof"
                  + " multiPolygon",
              multiPolygonFromTile);
          return;
        }
      }
    }

    DetectionResponse response =
        objectsDetector.apply(tileDetectionTask, mask, detectableObjectConfigurations);
    MachineDetectedTile machineDetectedTile =
        detectionMapper.toDetectedTile(
            response, tile, tileDetectionTask.getParcelId(), zoneDetectionJobId, parcelJobId);

    if (machineDetectedTile.getDetectedObjects() != null) {
      machineDetectedTile
          .getDetectedObjects()
          .forEach(
              detectedObject -> {
                if (address != null) {
                  detectedObject.getFeature().getProperties().put("address", address);
                }
                if (point != null) {
                  try {
                    var pointAsJson =
                        new ObjectMapper().findAndRegisterModules().writeValueAsString(point);
                    detectedObject.getFeature().getProperties().put("point", pointAsJson);
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                }
              });
    }
    machineDetectedTileRepository.save(machineDetectedTile);
  }

  public static TileDetectionTask withNewStatus(
      TileDetectionTask task,
      Status.ProgressionStatus progression,
      Status.HealthStatus health,
      String message) {
    return (TileDetectionTask)
        task.hasNewStatus(
            Status.builder()
                .progression(progression)
                .health(health)
                .creationDatetime(now())
                .message(message)
                .build());
  }
}
