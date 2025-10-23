package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.ZoneVggRequested;
import app.bpartners.geojobs.endpoint.event.model.annotation.AnnotationDeliveryJobRequested;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobSucceeded;
import app.bpartners.geojobs.repository.AnnotationDeliveryConfigurationRepository;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.service.DetectionFinishedMailer;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@AllArgsConstructor
public class ZoneDetectionJobSucceededService implements Consumer<ZoneDetectionJobSucceeded> {
  private static final String ZONE_DETECTION_JOB_WITHOUT_OBJECT_RESULT_TEMPLATE =
      "zone_detection_job_without_object_result";
  private final AnnotationDeliveryConfigurationRepository annotationDeliveryConfigurationRepository;
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final GeoJsonConversionJobService geoJsonConversionJobService;
  private final EventProducer eventProducer;
  private final MachineDetectedTileRepository machineDetectedTileRepository;
  private final DetectableObjectConfigurationRepository detectableObjectConfigurationRepository;
  private final DetectionFinishedMailer detectionFinishedMailer;
  private final HTMLTemplateParser htmlTemplateParser;
  private final DetectionRepository detectionRepository;

  @Override
  @Transactional
  public void accept(ZoneDetectionJobSucceeded event) {
    var succeededJobId = event.getSucceededJobId();
    var succeededZoneDetectionJob = zoneDetectionJobService.findById(succeededJobId);
    var detection = detectionRepository.findByZdjId(succeededJobId).orElse(null);
    var detectableObjectConfigurations =
        detectableObjectConfigurationRepository.findAllByDetectionJobId(succeededJobId);
    boolean machineDetectionFoundAnyDetectedTileFromDetectableConfiguration =
        detectableObjectConfigurations.stream()
            .anyMatch(
                detectableConfiguration -> {
                  var detectableType = detectableConfiguration.getObjectType().name();
                  var detectedTileCount =
                      machineDetectedTileRepository.countByZdjJobIdAndDetectableType(
                          succeededJobId, detectableType);
                  log.info(
                      "Detected tile count {} for detectableType {}",
                      detectedTileCount,
                      detectableType);
                  return detectedTileCount > 0;
                });
    if (!machineDetectionFoundAnyDetectedTileFromDetectableConfiguration) {
      var succeededDatetime = succeededZoneDetectionJob.getStatus().getCreationDatetime();
      var zoneName = succeededZoneDetectionJob.getZoneName();
      var formattedCreationDatetime =
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
              .format(succeededDatetime.atZone(ZoneId.of("Europe/Paris")));

      var emailSubject =
          String.format(
              "Analyse sur la zone %s terminée le %s sans objets détectés",
              zoneName, formattedCreationDatetime);
      var emailBody =
          detectionWithoutResultBody(
              detection, succeededZoneDetectionJob, detectableObjectConfigurations);
      detectionFinishedMailer.accept(
          succeededZoneDetectionJob.getEmailReceiver(), emailSubject, emailBody);
      return;
    }

    if (zoneDetectionJobService.countInDoubtDetectedTileToDeliveryById(succeededJobId) == 0L) {
      if (detection != null
          && detection.needsImageOutput()
          && detection.getPolygonGeoJsonZone() != null) {
        eventProducer.accept(List.of(new ZoneVggRequested(detection.getId())));
      }
      geoJsonConversionJobService.getOrComputeGeoJsonConversionJob(succeededZoneDetectionJob);
      return;
    }
    var minimumConfidenceForDelivery =
        annotationDeliveryConfigurationRepository
            .findLatestConfiguration()
            .orElseThrow(
                () -> new IllegalStateException("No annotation delivery configuration found"))
            .getMinimumConfidenceForDelivery();
    var annotationJobWithObjectsIdTruePositive = randomUUID().toString();
    var annotationJobWithObjectsIdFalsePositive = randomUUID().toString();
    var annotationJobWithoutObjectsId = randomUUID().toString();
    eventProducer.accept(
        List.of(
            AnnotationDeliveryJobRequested.builder()
                .jobId(succeededJobId)
                .minimumConfidenceForDelivery(minimumConfidenceForDelivery)
                .annotationJobWithObjectsIdTruePositive(annotationJobWithObjectsIdTruePositive)
                .annotationJobWithObjectsIdFalsePositive(annotationJobWithObjectsIdFalsePositive)
                .annotationJobWithoutObjectsId(annotationJobWithoutObjectsId)
                .build()));
  }

  private String detectionWithoutResultBody(
      @Nullable Detection detection,
      ZoneDetectionJob zoneDetectionJob,
      List<DetectableObjectConfiguration> detectableObjectConfigurations) {
    var detectableTypes =
        detectableObjectConfigurations.stream()
            .map(DetectableObjectConfiguration::getObjectType)
            .toList();
    Context context = new Context();
    context.setVariable("detection", detection);
    context.setVariable("zoneDetectionJob", zoneDetectionJob);
    context.setVariable("detectableTypes", detectableTypes);
    return htmlTemplateParser.apply(ZONE_DETECTION_JOB_WITHOUT_OBJECT_RESULT_TEMPLATE, context);
  }
}
