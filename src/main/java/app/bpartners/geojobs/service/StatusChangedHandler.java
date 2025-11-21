package app.bpartners.geojobs.service;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionStepUpdated;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.PojaEvent;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobStatusChanged;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusChangedHandler {
  private final DetectionRepository detectionRepository;
  private final EventProducer eventProducer;

  public void handle(
      PojaEvent event, Status newStatus, Status oldStatus, Runnable onFinish, Runnable onFailed) {
    var newProgression = newStatus.getProgression();
    var newHealth = newStatus.getHealth();

    if (oldStatus.equals(newStatus)) {
      log.info("Status did not change, yet change event received: event=" + event);
      return;
    }

    var illegalFinishedMessage = "Cannot finish as unknown or retrying, event=" + event;
    var notFinishedMessage = "Not finished yet, nothing to do, event=" + event;
    var doNothingMessage = "Old task already finished, do nothing";
    switch (oldStatus.getProgression()) {
      case PENDING, PROCESSING -> {
        switch (newProgression) {
          case FINISHED -> {
            switch (newHealth) {
              case UNKNOWN, RETRYING -> throw new IllegalStateException(illegalFinishedMessage);
              case SUCCEEDED -> {
                onFinish.run();
              }
              case FAILED -> {
                onFailed.run();
              }
            }
          }
          case PENDING, PROCESSING -> log.info(notFinishedMessage);
        }
      }
      case FINISHED -> log.info(doNothingMessage);
    }
    sendStatusUpdateEvent(event);
  }

  private void sendStatusUpdateEvent(PojaEvent event) {
    Optional<Detection> optionalDetection =
        switch (event) {
          case GeoJsonConversionJobStatusChanged geoJsonConversionJobStatusChanged -> {
            var job = geoJsonConversionJobStatusChanged.getNewJob();
            yield detectionRepository.findByZdjId(job.getZoneDetectionJobId());
          }
          case ZoneDetectionJobStatusChanged zoneDetectionJobStatusChanged -> {
            var zoneDetectionJobId =
                zoneDetectionJobStatusChanged.getNewJob().getZoneTilingJob().getId();
            yield detectionRepository.findByZdjId(zoneDetectionJobId);
          }
          case ZoneTilingJobStatusChanged zoneTilingJobStatusChanged -> {
            var zoneTilingJobId = zoneTilingJobStatusChanged.getNewJob().getId();
            yield detectionRepository.findByZtjId(zoneTilingJobId);
          }
          default -> Optional.empty();
        };

    if (optionalDetection.isEmpty()) {
      log.info("No detection attached to event {}", event);
      return;
    }

    Detection detection = optionalDetection.get();
    if (detection.isToNotify()) {
      eventProducer.accept(List.of(new DetectionStepUpdated(detection)));
    }
  }
}
