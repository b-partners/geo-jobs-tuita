package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.MACHINE_DETECTION;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.concurrency.Workers;
import app.bpartners.geojobs.endpoint.event.model.FeatureImageRequested;
import app.bpartners.geojobs.endpoint.event.model.FeatureVggRequested;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.event.FeatureImageRequestedService;
import app.bpartners.geojobs.service.event.FeatureVggRequestedService;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import app.bpartners.geojobs.service.tiling.ZoneTilingJobService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class SynchronousDetectionService
    implements Function<app.bpartners.geojobs.repository.model.detection.Detection, Detection> {
  private static final int MAX_RETRY_ATTEMPTS = 2;
  private final DetectionRepository detectionRepository;
  private final DetectionFromStatisticRestMapper detectionFromStatisticRestMapper;
  private final DetectionTilingCreation detectionTilingCreation;
  private final ZoneTilingJobService zoneTilingJobService;
  private final DetectionMachineDetectionCreation detectionMachineDetectionCreation;
  private final DetectionDelimitationRetriever detectionDelimitationRetriever;
  private final FeatureVggRequestedService zoneVggRequestedService;
  private final GeoJsonConversionJobService geoJsonConversionJobService;
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final Workers workers;
  private final DetectableObjectConfigurationRepository detectableObjectConfigurationRepository;
  private final FeatureImageRequestedService featureImageRequestedService;
  private final EntityManager entityManager;

  @SneakyThrows
  @Override
  public Detection apply(app.bpartners.geojobs.repository.model.detection.Detection detection) {
    detectionDelimitationRetriever.accept(detection);

    // Tiling step
    var detectionWithCreatedZTJ = detectionTilingCreation.processTiling(detection);
    var zoneTilingJobId = detectionWithCreatedZTJ.getZtjId();
    var tilingTasks = zoneTilingJobService.consumeTasks(zoneTilingJobId);
    var finishedZoneTilingJob = zoneTilingJobService.findById(zoneTilingJobId);

    // Machine detection job creation
    var createdZoneDetectionJob = zoneDetectionJobService.saveZDJFromZTJ(finishedZoneTilingJob);
    var zoneDetectionJobDetectableConf =
        detection.getDetectableObjectConfigurations().stream()
            .map(
                detectableObjectConfiguration ->
                    detectableObjectConfiguration.duplicate(
                        randomUUID().toString(), createdZoneDetectionJob.getId()))
            .toList();
    detectableObjectConfigurationRepository.saveAll(zoneDetectionJobDetectableConf);

    var detectionWithCreatedZDJ =
        detectionRepository.save(
            detectionWithCreatedZTJ.toBuilder().zdjId(createdZoneDetectionJob.getId()).build());

    Callable<Void> imageRequestCallableVoidList =
        () -> {
          featureImageRequestedService.accept(
              new FeatureImageRequested(detection.getId(), detection.getPolygonGeoJsonZone(), 0));
          return null;
        };
    Callable<Void> machineDetectionProcessCallableVoidList =
        () -> {
          // Machine detection step
          detectionMachineDetectionCreation.processMachineDetection(
              detectionWithCreatedZDJ, createdZoneDetectionJob, tilingTasks);
          return null;
        };
    List<Callable<Void>> firstCallableVoidList = new ArrayList<>();
    firstCallableVoidList.add(imageRequestCallableVoidList);
    firstCallableVoidList.add(machineDetectionProcessCallableVoidList);
    workers.invokeAll(firstCallableVoidList);

    List<Callable<Void>> secondVoidCallable =
        List.of(
            () -> {
              // VGG result computing step
              zoneVggRequestedService.accept(
                  new FeatureVggRequested(detection.getId(), detection.getPolygonGeoJsonZone(), 0));
              return null;
            },
            () -> {
              // GeoJson Result Requested __EVENT__ step
              geoJsonConversionJobService.getOrComputeGeoJsonConversionJob(createdZoneDetectionJob);
              return null;
            });
    workers.invokeAll(secondVoidCallable);

    return attemptVggFileKeyRetrieve(detection);
  }

  @SneakyThrows
  private Detection attemptVggFileKeyRetrieve(
      app.bpartners.geojobs.repository.model.detection.Detection detection) {
    log.info(
        "Waiting for ZoneVGGRequested to be computed for detection.e2Id: {}",
        detection.getEndToEndId());
    for (int attempt = 0; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      entityManager.clear();
      var actualDetection = detectionRepository.findById(detection.getId()).orElseThrow();

      if (actualDetection.getVggFileKey() != null) {
        return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
            actualDetection, FINISHED, SUCCEEDED, MACHINE_DETECTION);
      }

      if (attempt < MAX_RETRY_ATTEMPTS) {
        log.info(
            "VGG fileKey still null for detection.e2Id: {} (attempt {}/{}) → waiting 5s before"
                + " retry",
            detection.getEndToEndId(),
            attempt + 1,
            MAX_RETRY_ATTEMPTS);
        Thread.sleep(Duration.ofSeconds(5L));
      }
    }

    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        detectionRepository.findById(detection.getId()).orElseThrow(),
        FINISHED,
        SUCCEEDED,
        MACHINE_DETECTION);
  }
}
