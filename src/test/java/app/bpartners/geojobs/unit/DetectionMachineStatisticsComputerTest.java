package app.bpartners.geojobs.unit;

import static app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.SUCCEEDED;
import static app.bpartners.geojobs.endpoint.rest.model.Status.ProgressionEnum.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionStepStatisticMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.RoofDelimiterMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.StatusMapper;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.DetectionFeaturesResultImageRetriever;
import app.bpartners.geojobs.service.DetectionImageAttributeRetriever;
import app.bpartners.geojobs.service.DetectionVggAttributeRetriever;
import app.bpartners.geojobs.service.detection.DetectionMachineDetectionStatisticsComputer;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DetectionMachineStatisticsComputerTest {
  private static final String ZDJ_ID = "zdjId";
  StatusMapper<JobStatus> statusMapper = new StatusMapper<>();
  DetectionStepStatisticMapper detectionStepStatisticMapper =
      new DetectionStepStatisticMapper(statusMapper);
  BucketComponent bucketComponentMock = mock(BucketComponent.class);
  DetectionFromStatisticRestMapper detectionFromStatisticRestMapper;
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  DetectionFeaturesResultImageRetriever featureImageRetrieverMock =
      mock(DetectionFeaturesResultImageRetriever.class);
  DetectionImageAttributeRetriever imageAttributeRetrieverMock =
      mock(DetectionImageAttributeRetriever.class);
  DetectionVggAttributeRetriever vggAttributeRetrieverMock =
      mock(DetectionVggAttributeRetriever.class);
  RoofDelimiterMapper roofDelimiterMapperMock = mock();
  DetectionMachineDetectionStatisticsComputer subject;

  @BeforeEach
  void setUp() {
    when(featureImageRetrieverMock.apply(any()))
        .thenAnswer(invocation -> ((Detection) invocation.getArgument(0)).getProvidedGeoJsonZone());
    detectionFromStatisticRestMapper =
        new DetectionFromStatisticRestMapper(
            bucketComponentMock,
            detectionStepStatisticMapper,
            featureImageRetrieverMock,
            imageAttributeRetrieverMock,
            vggAttributeRetrieverMock,
            roofDelimiterMapperMock);
    subject =
        new DetectionMachineDetectionStatisticsComputer(
            detectionFromStatisticRestMapper, zoneDetectionJobServiceMock);

    when(bucketComponentMock.presign(anyString())).thenReturn(null);
    when(zoneDetectionJobServiceMock.getTaskStatistic(ZDJ_ID))
        .thenReturn(TaskStatistic.builder().taskStatusStatistics(List.of()).build());
  }

  @Test
  void compute_roofer_detection_statistic() {
    var actual = subject.apply(detection(), ZDJ_ID);

    assertEquals(
        FINISHED,
        Objects.requireNonNull(Objects.requireNonNull(actual.getStep()).getStatus())
            .getProgression());
    assertEquals(
        SUCCEEDED,
        Objects.requireNonNull(Objects.requireNonNull(actual.getStep()).getStatus()).getHealth());
  }

  public Detection detection() {
    return Detection.builder().zdjId(ZDJ_ID).geojsonS3FileKey("fileKey").isRooferMade(true).build();
  }
}
