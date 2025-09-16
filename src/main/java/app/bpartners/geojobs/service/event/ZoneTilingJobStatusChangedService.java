package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.ZoneImageRequested;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobCreated;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobFailed;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobStatusChanged;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.DetectionDelimitationRetriever;
import app.bpartners.geojobs.service.JobFinishedMailer;
import app.bpartners.geojobs.service.PointExtendedImageRequest;
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
  private final PointExtendedImageRequest pointExtendedImageRequest;

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
            pointExtendedImageRequest);

    var onFailedHandler = new onFailedJobHandler(eventProducer, newJob);

    statusChangedHandler.handle(
        event, newJob.getStatus(), oldJob.getStatus(), onSucceededHandler, onFailedHandler);
  }

  private record onSucceededJobHandler(
      EventProducer eventProducer,
      JobFinishedMailer<ZoneTilingJob> tilingFinishedMailer,
      ZoneDetectionJobService zoneDetectionJobService,
      ZoneTilingJob ztj,
      DetectionRepository detectionRepository,
      DetectableObjectConfigurationRepository objectConfigurationRepository,
      DetectionDelimitationRetriever detectionDelimitationRetriever,
      PointExtendedImageRequest pointExtendedImageRequest)
      implements Runnable {

    @Override
    public void run() {
      var zdj = zoneDetectionJobService.saveZDJFromZTJ(ztj);
      var optionalDetection = detectionRepository.findByZtjId(ztj.getId());
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

        savedDetection
            .getProvidedGeoJsonZone()
            .forEach(
                providedFeature ->
                    pointExtendedImageRequest.accept(savedDetection, providedFeature, false));

        if (savedDetection.getSplitPolygonGeoJsonZone() != null
            && !savedDetection.getSplitPolygonGeoJsonZone().isEmpty()
            && savedDetection.needsImageOutput()) {
          eventProducer.accept(
              List.of(
                  ZoneImageRequested.builder()
                      .detectionIdentifier(savedDetection.getId())
                      .build()));
        }
      }
      tilingFinishedMailer.accept(ztj);
      log.info("Finished, mail sent, ztj=" + ztj);
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
