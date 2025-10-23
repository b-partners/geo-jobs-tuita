package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.model.DetectionSucceeded;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.ZoneDetectionJobRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.service.DetectionFinishedMailer;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

@Service
@AllArgsConstructor
public class DetectionSucceededService implements Consumer<DetectionSucceeded> {
  private static final String DETECTION_SUCCEEDED_MAIL_TEMPLATE = "detection_succeeded_template";
  private final DetectionFinishedMailer mailer;
  private final HTMLTemplateParser htmlTemplateParser;
  private final DetectionRepository detectionRepository;
  private final BucketComponent bucketComponent;
  private final ZoneDetectionJobRepository zoneDetectionJobRepository;

  @Override
  @Transactional
  public void accept(DetectionSucceeded detectionSucceeded) {
    var detectionIdentifier = detectionSucceeded.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    if (detection.getStep() == null) {
      throw new NotImplementedException(
          "Only detection with persisted step can be handled by this service for now."
              + " Detection.id="
              + detectionIdentifier
              + " does not have persisted step");
    } else if (!detection.isOnStepPostProcessingSucceeded()) {
      throw new IllegalStateException(
          "Only detection on step POST_PROCESSING (FINISHED - SUCCEEDED) can be processed."
              + " Otherwise, actual Detection.id="
              + detectionIdentifier
              + " has step "
              + detection.getStep().toString());
    }
    var zoneDetectionJobIdentifier = detection.getZdjId();
    var succeededDatetime = detection.getStep().getCreationDatetime();
    var formattedSucceededDatetime =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .format(succeededDatetime.atZone(ZoneId.of("Europe/Paris")));
    var emailReceiver = detection.getEmailReceiver();
    var detectionJobStatus = actualStatusFromStep(detection);

    this.accept(
        zoneDetectionJobIdentifier,
        detectionJobStatus,
        detection.getZoneName(),
        formattedSucceededDatetime,
        emailReceiver);
  }

  public void accept(
      String zoneDetectionJobId,
      JobStatus detectionJobStatus,
      String zoneName,
      String formattedCreationDatetime,
      String emailReceiver) {
    var emailSubject =
        String.format("Analyse sur la zone %s terminée le %s", zoneName, formattedCreationDatetime);
    var emailBody = apply(zoneDetectionJobId, detectionJobStatus);

    mailer.accept(emailReceiver, emailSubject, emailBody);
  }

  private String apply(String zoneDetectionJobId, JobStatus detectionJobStatus) {
    Optional<ZoneDetectionJob> optionalZoneDetectionJob =
        zoneDetectionJobId == null
            ? Optional.empty()
            : zoneDetectionJobRepository.findById(zoneDetectionJobId);
    var optionalDetection = detectionRepository.findByZdjId(zoneDetectionJobId);
    var machineZoneDetectionJob = optionalZoneDetectionJob.orElse(null);
    var machineZoneDetectionJobStatus =
        machineZoneDetectionJob != null ? machineZoneDetectionJob.getStatus() : null;

    Context context = new Context();
    context.setVariable("zoneDetectionJobId", zoneDetectionJobId);
    context.setVariable("zoneDetectionJob", machineZoneDetectionJob);
    context.setVariable("detection", optionalDetection.orElse(null));
    context.setVariable(
        "geojsonUrl",
        optionalDetection
            .map(detection -> bucketComponent.presign(detection.getGeojsonS3FileKey()))
            .orElse(null));
    context.setVariable(
        "detectionSucceededDatetime",
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .format(detectionJobStatus.getCreationDatetime().atZone(ZoneId.of("Europe/Paris"))));
    if (machineZoneDetectionJobStatus != null) {
      context.setVariable(
          "zoneDetectionJobSucceededDatetime",
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
              .format(
                  machineZoneDetectionJobStatus
                      .getCreationDatetime()
                      .atZone(ZoneId.of("Europe/Paris"))));
    }

    return htmlTemplateParser.apply(DETECTION_SUCCEEDED_MAIL_TEMPLATE, context);
  }

  private JobStatus actualStatusFromStep(Detection detection) {
    return JobStatus.builder()
        .health(detection.getStep().getHealth())
        .progression(detection.getStep().getProgression())
        .creationDatetime(detection.getStep().getCreationDatetime())
        .build();
  }
}
