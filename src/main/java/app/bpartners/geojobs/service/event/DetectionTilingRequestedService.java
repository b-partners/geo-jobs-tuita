package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.REQUEST_ACCEPTED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionAreaUnsupported;
import app.bpartners.geojobs.endpoint.event.model.DetectionModelUnsupported;
import app.bpartners.geojobs.endpoint.event.model.DetectionTilingRequested;
import app.bpartners.geojobs.model.exception.UnsupportedDetectionAreaException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.DetectionStepRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import app.bpartners.geojobs.service.DetectionSupportedAreaValidator;
import app.bpartners.geojobs.service.DetectionSupportedModelValidator;
import app.bpartners.geojobs.service.detection.DetectionTilingCreation;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class DetectionTilingRequestedService implements Consumer<DetectionTilingRequested> {
  private final DetectionRepository detectionRepository;
  private final DetectionTilingCreation detectionTilingCreation;
  private final DetectionSupportedAreaValidator detectionAreaValidator;
  private final EventProducer eventProducer;
  private final DetectionStepRepository detectionStepRepository;
  private final DetectionSupportedModelValidator detectionSupportedModelValidator;

  @Override
  public void accept(DetectionTilingRequested detectionTilingRequested) {
    var detectionIdentifier = detectionTilingRequested.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    if (hasUnsupportedArea(detection)) return;
    if (hasUnsupportedModel(detection)) return;
    detectionTilingCreation.apply(detection);
  }

  private boolean hasUnsupportedModel(Detection detection) {
    try {
      detectionSupportedModelValidator.accept(detection);
    } catch (UnsupportedOperationException e) {
      log.error(e.getMessage());
      eventProducer.accept(List.of(new DetectionModelUnsupported(detection.getId())));
      detectionStepRepository.save(
          DetectionStep.builder()
              .id(randomUUID().toString())
              .detectionId(detection.getId())
              .name(REQUEST_ACCEPTED)
              .progression(PROCESSING)
              .health(UNKNOWN)
              .creationDatetime(now())
              .build());
      return true;
    }
    return false;
  }

  private boolean hasUnsupportedArea(Detection detection) {
    try {
      detectionAreaValidator.accept(detection);
    } catch (UnsupportedDetectionAreaException e) {
      log.error(e.getMessage());
      eventProducer.accept(
          List.of(new DetectionAreaUnsupported(detection.getId(), e.getComputedArea())));
      detectionStepRepository.save(
          DetectionStep.builder()
              .id(randomUUID().toString())
              .detectionId(detection.getId())
              .name(REQUEST_ACCEPTED)
              .progression(PROCESSING)
              .health(UNKNOWN)
              .creationDatetime(now())
              .build());
      return true;
    }
    return false;
  }
}
