package app.bpartners.geojobs.service.detection;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.MACHINE_DETECTION;

import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionMachineDetectionStatisticsComputer
    implements BiFunction<Detection, String, app.bpartners.geojobs.endpoint.rest.model.Detection> {
  private final DetectionFromStatisticRestMapper detectionFromStatisticRestMapper;
  private final ZoneDetectionJobService zoneDetectionJobService;

  @Override
  public app.bpartners.geojobs.endpoint.rest.model.Detection apply(
      Detection detection, String zdjId) {
    return detectionFromStatisticRestMapper.apply(
        detection, zoneDetectionJobService.computeTaskStatistics(zdjId), MACHINE_DETECTION);
  }
}
