package app.bpartners.geojobs.endpoint.event.consumer.parcel;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.parcel.ParcelDetectionJobCreated;
import app.bpartners.geojobs.endpoint.event.model.status.ParcelDetectionStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.status.ZDJStatusRecomputingSubmitted;
import app.bpartners.geojobs.repository.model.detection.ParcelDetectionJob;
import app.bpartners.geojobs.service.AnnotationRetrievingJobService;
import app.bpartners.geojobs.service.detection.RoofCoveringDetector;
import app.bpartners.geojobs.sqs.EventProducerInvocationMock;
import app.bpartners.geojobs.sqs.LocalEventQueue;
import app.bpartners.geojobs.utils.detection.DetectionIT;
import app.bpartners.geojobs.utils.detection.ObjectsDetectorMockResponse;
import java.time.Duration;
import java.util.List;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class ParcelDetectionJobCreatedIT extends DetectionIT {
  private static final double OBJECT_DETECTION_SUCCESS_RATE = 100.0;
  private static final int DEFAULT_EVENT_DELAY_SPEED_FACTOR = 10;
  private static final double MOCK_DETECTION_RESPONSE_CONFIDENCE = 1.0;
  @MockBean protected AnnotationRetrievingJobService annotationRetrievingJobServiceMock;
  @Autowired LocalEventQueue localEventQueue;
  @MockBean EventProducer eventProducerMock;
  @MockBean RoofCoveringDetector roofCoveringDetectorMock;
  EventProducerInvocationMock eventProducerInvocationMock = new EventProducerInvocationMock();

  @BeforeEach
  void setUp() {
    localEventQueue.configure(customEventConfigList(), DEFAULT_EVENT_DELAY_SPEED_FACTOR);
    doAnswer(
            invocationOnMock ->
                eventProducerInvocationMock.apply(localEventQueue, invocationOnMock))
        .when(eventProducerMock)
        .accept(any());
    when(roofCoveringDetectorMock.apply(any(), any())).thenReturn(null);
    when(jobAnnotationServiceMock.processAnnotationJob(any(), any())).thenReturn(null);
    when(annotationRetrievingJobServiceMock.findAllByDetectionJobId(any())).thenReturn(List.of());
    doNothing().when(mailerMock).accept(any());
    new ObjectsDetectorMockResponse(objectsDetectorMock)
        .apply(MOCK_DETECTION_RESPONSE_CONFIDENCE, "PISCINE", OBJECT_DETECTION_SUCCESS_RATE);
  }

  @NonNull
  private static List<LocalEventQueue.CustomEventDelayConfig> customEventConfigList() {
    return List.of(
        new LocalEventQueue.CustomEventDelayConfig(
            ParcelDetectionStatusRecomputingSubmitted.class, 50),
        new LocalEventQueue.CustomEventDelayConfig(ZDJStatusRecomputingSubmitted.class, 50));
  }

  @SneakyThrows
  @Test
  void single_event_and_zdj_succeeds() {
    SingleEventDataSetUp testData = getSingleEventDataSetUp();

    eventProducerMock.accept(
        List.of(
            ParcelDetectionJobCreated.builder()
                .parcelDetectionJob(testData.savedPDJ())
                .zdjId(testData.detectionJobId())
                .build()));

    // WARNING : No need for ZDJParcelsRecomputingSubmitted here as ParcelDetectionJobCreate
    // produces ParcelDetectionStatusRecomputingSubmitted
    eventProducerMock.accept(List.of(new ZDJStatusRecomputingSubmitted(testData.detectionJobId())));
    Thread.sleep(Duration.ofSeconds(15L));
    if (localEventQueue != null) localEventQueue.attemptSchedulerShutDown();

    var actualJob = zdjService.findById(testData.detectionJobId());
    assertTrue(actualJob.isSucceeded());
  }

  @NonNull
  private SingleEventDataSetUp getSingleEventDataSetUp() {
    String tilingJobId = randomUUID().toString();
    String detectionJobId = randomUUID().toString();
    String parcelDetectionTaskId = randomUUID().toString();
    String parcelDetectionJobId = randomUUID().toString();
    var tilingJob = ztjRepository.save(finishedZoneTilingJob(tilingJobId));
    var detectionJob = zdjService.save(processingZoneDetectionJob(detectionJobId, tilingJob));
    var parcel =
        parcelRepository.save(
            parcelCreator.create(
                randomUUID().toString(),
                List.of(tileCreator.create(randomUUID().toString(), "bucketPath"))));
    var parcelDetectionTask =
        parcelDetectionTaskRepository.save(
            parcelDetectionTaskCreator.create(
                parcelDetectionTaskId,
                detectionJob.getId(),
                parcelDetectionJobId,
                parcel,
                PROCESSING,
                UNKNOWN));
    var parcelDetectionJob = taskToJobConverter.apply(parcelDetectionTask);
    var tileDetectionTasks =
        parcelDetectionTask.getTiles().stream()
            .map(tile -> taskToJobConverter.apply(parcelDetectionTask, tile))
            .toList();
    var savedPDJ = pdjService.create(parcelDetectionJob, tileDetectionTasks);
    return new SingleEventDataSetUp(detectionJobId, savedPDJ);
  }

  private record SingleEventDataSetUp(String detectionJobId, ParcelDetectionJob savedPDJ) {}
}
