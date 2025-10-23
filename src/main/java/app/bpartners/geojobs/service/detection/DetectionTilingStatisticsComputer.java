package app.bpartners.geojobs.service.detection;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.TILING;

import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.tiling.ZoneTilingJobService;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionTilingStatisticsComputer
    implements BiFunction<Detection, String, app.bpartners.geojobs.endpoint.rest.model.Detection> {
  private final ZoneTilingJobService zoneTilingJobService;
  private final DetectionFromStatisticRestMapper detectionFromStatisticRestMapper;

  @Override
  public app.bpartners.geojobs.endpoint.rest.model.Detection apply(
      Detection detection, String tilingJobId) {
    return detectionFromStatisticRestMapper.apply(
        detection, zoneTilingJobService.computeTaskStatistics(tilingJobId), TILING);
  }
}
