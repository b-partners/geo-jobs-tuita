package app.bpartners.geojobs.endpoint.rest.mapper;

import static app.bpartners.geojobs.repository.model.GeoJobType.GEO_JSON_CONVERSION;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionStepStatisticMapper;
import app.bpartners.geojobs.endpoint.rest.model.DetectionStepName;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
import app.bpartners.geojobs.repository.model.GeoJobType;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionFromStatisticRestMapper
    implements TriFunction<
        Detection,
        TaskStatistic,
        DetectionStepName,
        app.bpartners.geojobs.endpoint.rest.model.Detection> {
  private final DetectionFromStepMapper detectionFromStepMapper;
  private final DetectionStepStatisticMapper detectionStepStatisticMapper;

  public app.bpartners.geojobs.endpoint.rest.model.Detection apply(
      Detection detection, TaskStatistic statistic, DetectionStepName detectionStepName) {
    var stepName =
        detectionStepStatisticMapper.toRestDetectionStepStatus(statistic, detectionStepName);
    return detectionFromStepMapper.apply(detection, stepName);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection computeEmptyStatisticFromStep(
      Detection detection,
      Status.ProgressionStatus progressionStatus,
      Status.HealthStatus healthStatus,
      DetectionStepName detectionStepName) {
    var geoJobType = fromDetectionStep(detectionStepName);
    var emptyStatistic =
        TaskStatistic.builder()
            .jobType(geoJobType)
            .actualJobStatus(
                JobStatus.builder()
                    .id(randomUUID().toString())
                    .creationDatetime(now())
                    .progression(progressionStatus)
                    .health(healthStatus)
                    .jobType(geoJobType)
                    .build())
            .updatedAt(now())
            .taskStatusStatistics(List.of())
            .build();
    return apply(detection, emptyStatistic, detectionStepName);
  }

  private GeoJobType fromDetectionStep(DetectionStepName stepName) {
    return switch (stepName) {
      case TILING -> GeoJobType.TILING;
      case REQUEST_ACCEPTED -> GeoJobType.REQUEST_ACCEPTED;
      case MACHINE_DETECTION -> GeoJobType.DETECTION;
      case POST_PROCESSING -> GEO_JSON_CONVERSION;
    };
  }
}
