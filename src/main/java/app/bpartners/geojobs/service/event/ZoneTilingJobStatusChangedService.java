package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.FeatureImageRequested;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobCreated;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobFailed;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobStatusChanged;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.DetectionDelimitationRetriever;
import app.bpartners.geojobs.service.JobFinishedMailer;
import app.bpartners.geojobs.service.StatusChangedHandler;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ZoneTilingJobStatusChangedService implements Consumer<ZoneTilingJobStatusChanged> {
  private final JobFinishedMailer<ZoneTilingJob> tilingFinishedMailer;
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final StatusChangedHandler statusChangedHandler;
  private final DetectionRepository detectionRepository;
  private final EventProducer eventProducer;
  private final DetectableObjectConfigurationRepository objectConfigurationRepository;
  private final DetectionDelimitationRetriever detectionDelimitationRetriever;
  private final TilingTaskRepository tilingTaskRepository;

  @Override
  public void accept(ZoneTilingJobStatusChanged event) {
    var oldJob = event.getOldJob();
    var newJob = event.getNewJob();

    var onSucceededHandler =
        new onSucceededJobHandler(
            eventProducer,
            tilingFinishedMailer,
            zoneDetectionJobService,
            newJob,
            detectionRepository,
            objectConfigurationRepository,
            detectionDelimitationRetriever,
            tilingTaskRepository);

    var onFailedHandler = new onFailedJobHandler(eventProducer, newJob);

    statusChangedHandler.handle(
        event, newJob.getStatus(), oldJob.getStatus(), onSucceededHandler, onFailedHandler);
  }

  private record onSucceededJobHandler(
      EventProducer eventProducer,
      JobFinishedMailer<ZoneTilingJob> tilingFinishedMailer,
      ZoneDetectionJobService zoneDetectionJobService,
      ZoneTilingJob zoneTilingJob,
      DetectionRepository detectionRepository,
      DetectableObjectConfigurationRepository objectConfigurationRepository,
      DetectionDelimitationRetriever detectionDelimitationRetriever,
      TilingTaskRepository tilingTaskRepository)
      implements Runnable {

    @Override
    public void run() {
      if (tilingTaskRepository.findAllByJobId(zoneTilingJob.getId()).stream()
          .anyMatch(
              task ->
                  task.getTiles().stream()
                      .anyMatch(tile -> tile.getBucketPath().contains(".xml")))) {
        eventProducer.accept(List.of(new ZoneTilingJobFailed(zoneTilingJob)));
        return;
      }
      var zdj = zoneDetectionJobService.saveZDJFromZTJ(zoneTilingJob);
      var optionalDetection = detectionRepository.findByZtjId(zoneTilingJob.getId());
      // For now, only detection process triggers ZDJ processing
      if (optionalDetection.isPresent()) {
        var detection = optionalDetection.get();
        var savedDetection =
            detectionRepository.save(detection.toBuilder().zdjId(zdj.getId()).build());
        objectConfigurationRepository.saveAll(
            savedDetection.getDetectableObjectConfigurations().stream()
                .map(
                    objectConfiguration ->
                        objectConfiguration.duplicate(randomUUID().toString(), zdj.getId()))
                .toList());
        eventProducer.accept(
            List.of(ZoneDetectionJobCreated.builder().zoneDetectionJob(zdj).build()));

        detectionDelimitationRetriever.accept(savedDetection);

        if (savedDetection.needsImageOutput()) {
          var detectionIdentifier = savedDetection.getId();
          var providedGeoJsonZone = savedDetection.getProvidedGeoJsonZone();
          for (int i = 0; i < providedGeoJsonZone.size(); i++) {
            eventProducer.accept(
                List.of(
                    new FeatureImageRequested(detectionIdentifier, providedGeoJsonZone.get(i), i)));
          }
        }
      }
      tilingFinishedMailer.accept(zoneTilingJob);
      log.info("Finished, mail sent, ztj=" + zoneTilingJob);
    }
  }

  private record onFailedJobHandler(EventProducer eventProducer, ZoneTilingJob failedJob)
      implements Runnable {
    @Override
    public void run() {
      eventProducer.accept(List.of(new ZoneTilingJobFailed(failedJob)));
      log.info("Finished with failed status, ztj=" + failedJob);
    }
  }
}
