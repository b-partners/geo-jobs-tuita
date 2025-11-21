package app.bpartners.geojobs.service;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionTaskCreated;
import app.bpartners.geojobs.job.repository.JobStatusRepository;
import app.bpartners.geojobs.job.repository.TaskRepository;
import app.bpartners.geojobs.job.service.JobService;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.TaskStatisticRepository;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionJob;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionAddressConversionJobService
    extends JobService<DetectionAddressConversionTask, DetectionAddressConversionJob> {
  private final DetectionRepository detectionRepository;
  private final CommunityAuthorizationRepository communityAuthorizationRepository;

  protected DetectionAddressConversionJobService(
      JpaRepository<DetectionAddressConversionJob, String> repository,
      JobStatusRepository jobStatusRepository,
      TaskStatisticRepository taskStatisticRepository,
      TaskRepository<DetectionAddressConversionTask> taskRepository,
      EventProducer eventProducer,
      DetectionRepository detectionRepository,
      CommunityAuthorizationRepository communityAuthorizationRepository) {
    super(
        repository,
        jobStatusRepository,
        taskStatisticRepository,
        taskRepository,
        eventProducer,
        DetectionAddressConversionJob.class);
    this.detectionRepository = detectionRepository;
    this.communityAuthorizationRepository = communityAuthorizationRepository;
  }

  @Transactional
  public DetectionAddressConversionJob fireTasks(String jobId) {
    var job = findById(jobId);
    var detection = detectionRepository.findById(job.getDetectionId()).orElseThrow();
    getTasks(job)
        .forEach(
            task -> eventProducer.accept(List.of(new DetectionAddressConversionTaskCreated(task))));

    eventProducer.accept(
        List.of(new DetectionAddressConversionJobStatusRecomputingSubmitted(jobId)));

    return job;
  }

  @Override
  protected void onStatusChanged(
      DetectionAddressConversionJob oldJob, DetectionAddressConversionJob newJob) {
    eventProducer.accept(
        List.of(
            DetectionAddressConversionJobStatusChanged.builder()
                .oldJob(oldJob)
                .newJob(newJob)
                .build()));
  }
}
