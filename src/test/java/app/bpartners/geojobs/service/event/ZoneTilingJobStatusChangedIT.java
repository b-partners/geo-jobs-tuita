package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobStatusChanged;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.JobFinishedMailer;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class ZoneTilingJobStatusChangedIT extends FacadeIT {
  @Autowired private ZoneTilingJobStatusChangedService subject;
  @MockBean private JobFinishedMailer<ZoneTilingJob> mailer;
  @MockBean private ZoneDetectionJobService zdjService;
  @MockBean private DetectionRepository detectionRepository;

  @Test
  void send_email_ok() {
    var jobId = randomUUID().toString();
    var statusHistory = new ArrayList<JobStatus>();
    statusHistory.add(
        JobStatus.builder()
            .id(randomUUID().toString())
            .jobId(jobId)
            .progression(FINISHED)
            .health(SUCCEEDED)
            .build());
    var newZtj =
        ZoneTilingJob.builder()
            .id(jobId)
            .zoneName("mock")
            .emailReceiver("mock@gmail.com")
            .statusHistory(statusHistory)
            .build();
    var zoneTilingJobStatusChanged =
        ZoneTilingJobStatusChanged.builder().oldJob(new ZoneTilingJob()).newJob(newZtj).build();
    when(zdjService.saveZDJFromZTJ(any()))
        .thenReturn(ZoneDetectionJob.builder().id("zdj_id").build());
    when(detectionRepository.findByEndToEndIdAndCommunityOwnerId(any(), any()))
        .thenReturn(Optional.ofNullable(Detection.builder().build()));
    subject.accept(zoneTilingJobStatusChanged);

    verify(zdjService, times(1)).saveZDJFromZTJ(zoneTilingJobStatusChanged.getNewJob());
    verify(mailer, times(1)).accept(any());
  }
}
