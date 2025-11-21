package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.model.TaskCreated;
import app.bpartners.geojobs.job.model.Task;
import app.bpartners.geojobs.job.repository.TaskRepository;
import app.bpartners.geojobs.job.service.TaskStatusService;
import app.bpartners.geojobs.service.TaskConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TaskCreatedService<T extends Task, C extends TaskCreated<T>> implements Consumer<C> {
  private static final int MAX_ATTEMPT_NB = 5;
  private final TaskConsumer<T> taskConsumer;
  private final TaskStatusService<T> taskStatusService;
  private final TaskRepository<T> taskRepository;

  @Override
  public void accept(C event) {
    var task = event.getTask();
    var actualAttemptNb = event.getAttemptNb();
    var newAttemptNb = actualAttemptNb + 1;

    if (actualAttemptNb == 1) {
      taskStatusService.process(task);
    }

    if (newAttemptNb > MAX_ATTEMPT_NB) {
      log.info(
          "Task [{} - id={}] reached attempt {}/{}",
          task,
          task.getId(),
          newAttemptNb,
          MAX_ATTEMPT_NB);
      fail(task);
      return;
    }

    taskConsumer.accept(task);

    succeed(task);
  }

  private void succeed(T task) {
    taskRepository.save(task);
    taskStatusService.succeed(task);
  }

  private void fail(T task) {
    taskRepository.save(task);
    taskStatusService.fail(task);
  }
}
