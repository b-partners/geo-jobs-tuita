package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.FAILED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static java.time.Instant.now;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionTaskCreated;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.job.service.TaskStatusService;
import app.bpartners.geojobs.repository.DetectionAddressConversionTaskRepository;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import app.bpartners.geojobs.service.TaskConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

@Service
public class DetectionAddressConversionTaskCreatedService
    extends TaskCreatedService<
        DetectionAddressConversionTask, DetectionAddressConversionTaskCreated>
    implements Consumer<DetectionAddressConversionTaskCreated> {
  private static final String API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_BAD_ADDRESS_PROVIDED =
      "{\"type\":\"500 INTERNAL_SERVER_ERROR\",\"message\":\"Index 0 out of bounds for length 0\"}";
  private final DetectionAddressConversionTaskRepository taskRepository;

  public DetectionAddressConversionTaskCreatedService(
      TaskConsumer<DetectionAddressConversionTask> taskConsumer,
      TaskStatusService<DetectionAddressConversionTask> taskStatusService,
      DetectionAddressConversionTaskRepository taskRepository) {
    super(taskConsumer, taskStatusService, taskRepository);
    this.taskRepository = taskRepository;
  }

  @Override
  public void accept(DetectionAddressConversionTaskCreated event) {
    var task = event.getTask();
    try {
      super.accept(event);
    } catch (HttpServerErrorException.InternalServerError apiException) {
      if (apiException.getStatusCode() == INTERNAL_SERVER_ERROR) {
        var apiExceptionResponseBodyAsString = apiException.getResponseBodyAsString();
        if (API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_BAD_ADDRESS_PROVIDED.equals(
            apiExceptionResponseBodyAsString)) {
          task.hasNewStatus(
              Status.builder()
                  .progression(FINISHED)
                  .health(FAILED)
                  .creationDatetime(now())
                  .message(
                      "Unable to convert address "
                          + task.getAddress()
                          + " to coordinates latitude and longitude")
                  .build());
          taskRepository.save(task);
        } else {
          throw new RuntimeException(
              "Unexpected internal server error causing retry from dashboard API : "
                  + apiExceptionResponseBodyAsString);
        }
      }
    }
  }
}
