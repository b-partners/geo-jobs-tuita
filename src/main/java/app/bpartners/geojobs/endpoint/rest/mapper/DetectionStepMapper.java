package app.bpartners.geojobs.endpoint.rest.mapper;

import app.bpartners.geojobs.endpoint.rest.model.DetectionStep;
import app.bpartners.geojobs.endpoint.rest.model.DetectionStepName;
import app.bpartners.geojobs.endpoint.rest.model.Status;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DetectionStepMapper {
  public app.bpartners.geojobs.repository.model.detection.DetectionStep toDomain(
      String detectionIdentifier, DetectionStep step) {
    return app.bpartners.geojobs.repository.model.detection.DetectionStep.builder()
        .id(UUID.randomUUID().toString())
        .detectionId(detectionIdentifier)
        .name(step.getName())
        .progression(
            app.bpartners.geojobs.job.model.Status.ProgressionStatus.valueOf(
                step.getStatus().getProgression().getValue()))
        .health(
            app.bpartners.geojobs.job.model.Status.HealthStatus.valueOf(
                step.getStatus().getHealth().getValue()))
        .creationDatetime(Instant.now())
        .build();
  }

  public DetectionStep toRest(app.bpartners.geojobs.repository.model.detection.DetectionStep step) {
    return new DetectionStep()
        .name(DetectionStepName.fromValue(step.getName().getValue()))
        .status(
            new app.bpartners.geojobs.endpoint.rest.model.Status()
                .creationDatetime(step.getCreationDatetime())
                .progression(
                    Status.ProgressionEnum.valueOf(
                        step.getProgression().name())) // TODO: use existing restMapper
                .health(
                    Status.HealthEnum.valueOf(
                        step.getHealth().name()))); // TODO: user existing restMapper
  }
}
