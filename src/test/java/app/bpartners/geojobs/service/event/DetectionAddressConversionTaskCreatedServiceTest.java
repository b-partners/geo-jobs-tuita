package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionTaskCreated;
import app.bpartners.geojobs.repository.DetectionAddressConversionTaskRepository;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import app.bpartners.geojobs.service.DetectionAddressConversionTaskConsumer;
import app.bpartners.geojobs.service.DetectionAddressConversionTaskStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpServerErrorException;

class DetectionAddressConversionTaskCreatedServiceTest {
  private static final String API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_BAD_ADDRESS_PROVIDED =
      "{\"type\":\"500 INTERNAL_SERVER_ERROR\",\"message\":\"Index 0 out of bounds for length 0\"}";
  private static final String API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_UNKNOWN_API_EXCEPTION =
      "{\"type\":\"500 INTERNAL_SERVER_ERROR\",\"message\":\"Unknown exception\"}";
  DetectionAddressConversionTaskConsumer taskConsumerMock = mock();
  DetectionAddressConversionTaskStatusService taskStatusServiceMock = mock();
  DetectionAddressConversionTaskRepository taskRepositoryMock = mock();
  DetectionAddressConversionTaskCreatedService subject =
      new DetectionAddressConversionTaskCreatedService(
          taskConsumerMock, taskStatusServiceMock, taskRepositoryMock);

  @Test
  void consumes_task_and_succeeded_event_with_first_attempt() {
    var taskMock = mock(DetectionAddressConversionTask.class);
    var detectionAddressConversionTaskCreated = mock(DetectionAddressConversionTaskCreated.class);
    int attemptNb = 1;
    when(detectionAddressConversionTaskCreated.getAttemptNb()).thenReturn(attemptNb);
    when(detectionAddressConversionTaskCreated.getTask()).thenReturn(taskMock);
    doNothing().when(taskConsumerMock).accept(taskMock);

    assertDoesNotThrow(() -> subject.accept(detectionAddressConversionTaskCreated));

    verify(taskStatusServiceMock).process(taskMock);
    verify(taskConsumerMock, only()).accept(taskMock);
    verify(taskRepositoryMock, only()).save(taskMock);
    verify(taskStatusServiceMock).succeed(taskMock);
  }

  @Test
  void consumes_task_and_succeeded_event_with_attempt_less_than_max_attempts() {
    var taskMock = mock(DetectionAddressConversionTask.class);
    var detectionAddressConversionTaskCreated = mock(DetectionAddressConversionTaskCreated.class);
    int attemptNb = 3;
    when(detectionAddressConversionTaskCreated.getAttemptNb()).thenReturn(attemptNb);
    when(detectionAddressConversionTaskCreated.getTask()).thenReturn(taskMock);
    doNothing().when(taskConsumerMock).accept(taskMock);

    assertDoesNotThrow(() -> subject.accept(detectionAddressConversionTaskCreated));

    verify(taskStatusServiceMock, never()).process(taskMock);
    verify(taskConsumerMock, only()).accept(taskMock);
    verify(taskRepositoryMock, only()).save(taskMock);
    verify(taskStatusServiceMock, only()).succeed(taskMock);
  }

  @Test
  void consumes_task_with_out_of_bounds_500_api_exception_fail() {
    var e2ApiKey = randomUUID().toString();
    var attemptNb = 1;
    var detectionAddressConversionTaskCreatedMock =
        mock(DetectionAddressConversionTaskCreated.class);
    var task = DetectionAddressConversionTask.builder().address("My bad address").build();
    when(detectionAddressConversionTaskCreatedMock.getAttemptNb()).thenReturn(attemptNb);
    when(detectionAddressConversionTaskCreatedMock.getTask()).thenReturn(task);
    doThrow(
            HttpServerErrorException.create(
                INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                null,
                API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_BAD_ADDRESS_PROVIDED.getBytes(),
                null))
        .when(taskConsumerMock)
        .accept(task);
    when(taskRepositoryMock.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertDoesNotThrow(() -> subject.accept(detectionAddressConversionTaskCreatedMock));

    verify(taskStatusServiceMock).process(task);
    verify(taskConsumerMock, only()).accept(task);
    verify(taskStatusServiceMock, never()).fail(task);
    verify(taskStatusServiceMock, never()).succeed(task);

    var taskCaptor = ArgumentCaptor.forClass(DetectionAddressConversionTask.class);
    verify(taskRepositoryMock, only()).save(taskCaptor.capture());
    var taskSaved = taskCaptor.getValue();
    var actualTaskStatus = taskSaved.getStatus();
    assertTrue(taskSaved.isFailed());
    assertEquals(
        "Unable to convert address " + task.getAddress() + " to coordinates latitude and longitude",
        actualTaskStatus.getMessage());
  }

  @Test
  void consumes_task_with_unknown_500_api_exception_rethrows_exception() {
    var attemptNb = 1;
    var detectionAddressConversionTaskCreatedMock =
        mock(DetectionAddressConversionTaskCreated.class);
    var task = DetectionAddressConversionTask.builder().address("My bad address").build();
    when(detectionAddressConversionTaskCreatedMock.getAttemptNb()).thenReturn(attemptNb);
    when(detectionAddressConversionTaskCreatedMock.getTask()).thenReturn(task);
    doThrow(
            HttpServerErrorException.create(
                INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                null,
                API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_UNKNOWN_API_EXCEPTION.getBytes(),
                null))
        .when(taskConsumerMock)
        .accept(task);
    when(taskRepositoryMock.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var actual =
        assertThrows(
            RuntimeException.class,
            () -> subject.accept(detectionAddressConversionTaskCreatedMock));

    verify(taskStatusServiceMock).process(task);
    verify(taskConsumerMock, only()).accept(task);
    verify(taskStatusServiceMock, never()).fail(task);
    verify(taskStatusServiceMock, never()).succeed(task);
    verify(taskRepositoryMock, never()).save(task);
    assertEquals(
        "Unexpected internal server error causing retry from dashboard API : "
            + API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_UNKNOWN_API_EXCEPTION,
        actual.getMessage());
  }

  @Test
  void max_attempt_nb_reached_and_fail_event() {
    var taskMock = mock(DetectionAddressConversionTask.class);
    var detectionAddressConversionTaskCreatedMock =
        mock(DetectionAddressConversionTaskCreated.class);
    var attemptNb = 6;
    when(detectionAddressConversionTaskCreatedMock.getAttemptNb()).thenReturn(attemptNb);
    when(detectionAddressConversionTaskCreatedMock.getTask()).thenReturn(taskMock);
    doNothing().when(taskConsumerMock).accept(taskMock);

    assertDoesNotThrow(() -> subject.accept(detectionAddressConversionTaskCreatedMock));

    verify(taskStatusServiceMock, never()).process(taskMock);
    verify(taskConsumerMock, never()).accept(taskMock);
    verify(taskStatusServiceMock, only()).fail(taskMock);
    verify(taskRepositoryMock, only()).save(taskMock);
  }
}
