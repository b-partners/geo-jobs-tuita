package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_2;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.ZoneVggRequested;
import app.bpartners.geojobs.endpoint.event.model.annotation.AnnotationDeliveryJobRequested;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobSucceeded;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.repository.AnnotationDeliveryConfigurationRepository;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.annotation.AnnotationDeliveryConfiguration;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.service.DetectionFinishedMailer;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZoneDetectionJobSucceededServiceTest {
  private static final double MIN_CONFIDENCE_FOR_DELIVERY = 0.95;
  AnnotationDeliveryConfigurationRepository configurationRepositoryMock = mock();
  EventProducer eventProducerMock = mock();
  GeoJsonConversionJobService geoJsonConversionJobServiceMock = mock();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  MachineDetectedTileRepository machineDetectedTileRepositoryMock = mock();
  DetectableObjectConfigurationRepository detectableObjectConfigurationRepositoryMock = mock();
  DetectionFinishedMailer detectionFinishedMailerMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  HTMLTemplateParser htmlTemplateParser = new HTMLTemplateParser();
  ZoneDetectionJobSucceededService subject =
      new ZoneDetectionJobSucceededService(
          configurationRepositoryMock,
          zoneDetectionJobServiceMock,
          geoJsonConversionJobServiceMock,
          eventProducerMock,
          machineDetectedTileRepositoryMock,
          detectableObjectConfigurationRepositoryMock,
          detectionFinishedMailerMock,
          htmlTemplateParser,
          detectionRepositoryMock);

  @BeforeEach
  void setUp() {
    // so that detection always has detected tile - must be overridden for specific test
    when(machineDetectedTileRepositoryMock.countByZdjJobIdAndDetectableType(any(), any()))
        .thenReturn(1L);
    when(detectableObjectConfigurationRepositoryMock.findAllByDetectionJobId(any()))
        .thenReturn(someObjectConfigurations());
  }

  private @NotNull List<DetectableObjectConfiguration> someObjectConfigurations() {
    return List.of(
        DetectableObjectConfiguration.builder().objectType(USURE_IMPORTANTE).build(),
        DetectableObjectConfiguration.builder().objectType(MOISISSURE_NOIRCIE).build());
  }

  @Test
  void trigger_mail_with_ZDJ_infos_when_no_detect_tile_found() {
    var succeededJobId = randomUUID().toString();
    var succeededZoneDetectionJobMock = mock(ZoneDetectionJob.class);
    var jobStatus = mock(JobStatus.class);
    reset(machineDetectedTileRepositoryMock);
    when(machineDetectedTileRepositoryMock.countByZdjJobIdAndDetectableType(
            succeededJobId, USURE_IMPORTANTE.name()))
        .thenReturn(0L);
    when(machineDetectedTileRepositoryMock.countByZdjJobIdAndDetectableType(
            succeededJobId, MOISISSURE_NOIRCIE.name()))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(succeededJobId))
        .thenReturn(1L);
    var emailReceiver = "email@email.com";
    var zoneName = "My address";
    var creationDatetime = Instant.parse("2025-03-01T03:00:00Z");
    var emailSubject =
        String.format(
            "Analyse sur la zone %s terminée le %s sans objets détectés",
            zoneName,
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .format(creationDatetime.atZone(ZoneId.of("Europe/Paris"))));
    when(succeededZoneDetectionJobMock.getId()).thenReturn(succeededJobId);
    when(succeededZoneDetectionJobMock.getEmailReceiver()).thenReturn(emailReceiver);
    when(succeededZoneDetectionJobMock.getZoneName()).thenReturn(zoneName);
    when(jobStatus.getCreationDatetime()).thenReturn(creationDatetime);
    when(succeededZoneDetectionJobMock.getStatus()).thenReturn(jobStatus);
    when(zoneDetectionJobServiceMock.findById(succeededJobId))
        .thenReturn(succeededZoneDetectionJobMock);
    when(detectionRepositoryMock.findByZdjId(succeededJobId)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> subject.accept(new ZoneDetectionJobSucceeded(succeededJobId)));

    var emailBodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(detectionFinishedMailerMock, only())
        .accept(eq(emailReceiver), eq(emailSubject), emailBodyCaptor.capture());
    verify(configurationRepositoryMock, never()).findLatestConfiguration();
    verify(eventProducerMock, never()).accept(any());
    assertEquals(
        expectedEmailContainingZDJWhenNoResultRetrieved(succeededJobId),
        emailBodyCaptor.getValue());
  }

  @Test
  void trigger_mail_with_detection_infos_when_no_detect_tile_found() {
    var succeededJobId = randomUUID().toString();
    var succeededZoneDetectionJobMock = mock(ZoneDetectionJob.class);
    var jobStatus = mock(JobStatus.class);
    reset(machineDetectedTileRepositoryMock);
    when(machineDetectedTileRepositoryMock.countByZdjJobIdAndDetectableType(
            succeededJobId, USURE_IMPORTANTE.name()))
        .thenReturn(0L);
    when(machineDetectedTileRepositoryMock.countByZdjJobIdAndDetectableType(
            succeededJobId, MOISISSURE_NOIRCIE.name()))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(succeededJobId))
        .thenReturn(1L);
    var emailReceiver = "email@email.com";
    var zoneName = "My address";
    var creationDatetime = Instant.parse("2025-03-01T03:00:00Z");
    var emailSubject =
        String.format(
            "Analyse sur la zone %s terminée le %s sans objets détectés",
            zoneName,
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .format(creationDatetime.atZone(ZoneId.of("Europe/Paris"))));
    when(succeededZoneDetectionJobMock.getId()).thenReturn(succeededJobId);
    when(succeededZoneDetectionJobMock.getEmailReceiver()).thenReturn(emailReceiver);
    when(succeededZoneDetectionJobMock.getZoneName()).thenReturn(zoneName);
    when(jobStatus.getCreationDatetime()).thenReturn(creationDatetime);
    when(succeededZoneDetectionJobMock.getStatus()).thenReturn(jobStatus);
    when(zoneDetectionJobServiceMock.findById(succeededJobId))
        .thenReturn(succeededZoneDetectionJobMock);
    var detectionMock = mock(Detection.class);
    var detectionE2Id = randomUUID().toString();
    when(detectionMock.getEndToEndId()).thenReturn(detectionE2Id);
    when(detectionMock.getZoneName()).thenReturn(zoneName);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionRepositoryMock.findByZdjId(succeededJobId))
        .thenReturn(Optional.of(detectionMock));

    assertDoesNotThrow(() -> subject.accept(new ZoneDetectionJobSucceeded(succeededJobId)));

    var emailBodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(detectionFinishedMailerMock, only())
        .accept(eq(emailReceiver), eq(emailSubject), emailBodyCaptor.capture());
    verify(configurationRepositoryMock, never()).findLatestConfiguration();
    verify(eventProducerMock, never()).accept(any());
    assertEquals(
        expectedEmailContainingDetectionWhenNoResultRetrieved(detectionE2Id),
        emailBodyCaptor.getValue());
  }

  @Test
  void succeeded_and_triggers_annotation_delivery_job_requested() {
    var annotationDeliveryConfigurationMock = mock(AnnotationDeliveryConfiguration.class);
    var succeededJobId = randomUUID().toString();
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(succeededJobId))
        .thenReturn(1L);
    when(annotationDeliveryConfigurationMock.getMinimumConfidenceForDelivery())
        .thenReturn(MIN_CONFIDENCE_FOR_DELIVERY);
    when(configurationRepositoryMock.findLatestConfiguration())
        .thenReturn(Optional.of(annotationDeliveryConfigurationMock));

    subject.accept(ZoneDetectionJobSucceeded.builder().succeededJobId(succeededJobId).build());

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var annotationJobDeliveryRequested =
        ((AnnotationDeliveryJobRequested) listCaptor.getValue().getFirst());
    assertNotNull(annotationJobDeliveryRequested.getAnnotationJobWithObjectsIdTruePositive());
    assertNotNull(annotationJobDeliveryRequested.getAnnotationJobWithObjectsIdFalsePositive());
    assertNotNull(annotationJobDeliveryRequested.getAnnotationJobWithoutObjectsId());
    assertEquals(succeededJobId, annotationJobDeliveryRequested.getJobId());
  }

  @Test
  void fails_to_find_annotation_delivery_configuration() {
    var succeededJobId = randomUUID().toString();
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(succeededJobId))
        .thenReturn(1L);
    when(configurationRepositoryMock.findLatestConfiguration()).thenReturn(Optional.empty());

    var actual =
        assertThrows(
            IllegalStateException.class,
            () ->
                subject.accept(
                    ZoneDetectionJobSucceeded.builder().succeededJobId(succeededJobId).build()));

    assertEquals("No annotation delivery configuration found", actual.getMessage());
  }

  @Test
  void process_geo_json_conversion_job_when_any_in_doubt_detected_tile() {
    var succeededJobId = randomUUID().toString();
    var zoneDetectionJobMock = mock(ZoneDetectionJob.class);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(succeededJobId))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.findById(succeededJobId)).thenReturn(zoneDetectionJobMock);

    assertDoesNotThrow(
        () ->
            subject.accept(
                ZoneDetectionJobSucceeded.builder().succeededJobId(succeededJobId).build()));

    verify(geoJsonConversionJobServiceMock, times(1))
        .getOrComputeGeoJsonConversionJob(zoneDetectionJobMock);
    verify(configurationRepositoryMock, never()).findLatestConfiguration();
    verify(eventProducerMock, never()).accept(any());
  }

  @Test
  void process_geo_json_conversion_job_and_produces_event_when_any_in_doubt_detected_tile() {
    var succeededJobId = randomUUID().toString();
    var detectionId = randomUUID().toString();
    var zoneDetectionJobMock = mock(ZoneDetectionJob.class);
    var detectionMock = mock(Detection.class);
    when(detectionMock.getId()).thenReturn(detectionId);
    when(detectionMock.needsImageOutput()).thenReturn(true);
    when(detectionMock.getPolygonGeoJsonZone()).thenReturn(new Feature());
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(succeededJobId))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.findById(succeededJobId)).thenReturn(zoneDetectionJobMock);
    when(detectionRepositoryMock.findByZdjId(succeededJobId))
        .thenReturn(Optional.of(detectionMock));

    assertDoesNotThrow(
        () ->
            subject.accept(
                ZoneDetectionJobSucceeded.builder().succeededJobId(succeededJobId).build()));

    verify(geoJsonConversionJobServiceMock, times(1))
        .getOrComputeGeoJsonConversionJob(zoneDetectionJobMock);
    verify(configurationRepositoryMock, never()).findLatestConfiguration();
    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var actualZoneVggRequested = (ZoneVggRequested) listCaptor.getAllValues().getLast().getFirst();
    assertEquals(new ZoneVggRequested(detectionId), actualZoneVggRequested);
    assertEquals(EVENT_STACK_2, actualZoneVggRequested.getEventStack());
    assertEquals(Duration.ofSeconds(30L), actualZoneVggRequested.maxConsumerDuration());
    assertEquals(
        Duration.ofSeconds(30L), actualZoneVggRequested.maxConsumerBackoffBetweenRetries());
  }

  private String expectedEmailContainingDetectionWhenNoResultRetrieved(String detectionE2Id) {
    return String.format(
        """
<html>
<head>
    <style>
        body {
            font-family: Helvetica, serif;
        }
    </style>
</head>
<body>
<section>
    <p>Bonjour,</p>
    <div>
        <p>La détection portant l'identifiant <span>%s</span> effectuée sur la zone
            <span>My address</span> n'a permis de trouver aucun objet
            correspondant à la liste d'objets suivante, contenu dans le modèle de détection <strong>BP_TOITURE</strong> :</p>
    </div>
   \s
    <ul>
        <li>
            <span>USURE_IMPORTANTE</span>
        </li>
        <li>
            <span>MOISISSURE_NOIRCIE</span>
        </li>
    </ul>
    <p>Cordialement.</p>
    <p>L'équipe BirdIA.</p>
</section>
</body>
</html>""",
        detectionE2Id);
  }

  private @NotNull String expectedEmailContainingZDJWhenNoResultRetrieved(
      String zoneDetectionJobId) {
    return String.format(
        """
        <html>
        <head>
            <style>
                body {
                    font-family: Helvetica, serif;
                }
            </style>
        </head>
        <body>
        <section>
            <p>Bonjour,</p>
           \s
            <div>
                <p>La détection machine portant l'identifiant <span>%s</span> effectuée sur la
                    zone <span>My address</span> n'a permis de trouver aucun objet
                    correspondant à la liste d'objets suivante :</p>
            </div>
            <ul>
                <li>
                    <span>USURE_IMPORTANTE</span>
                </li>
                <li>
                    <span>MOISISSURE_NOIRCIE</span>
                </li>
            </ul>
            <p>Cordialement.</p>
            <p>L'équipe BirdIA.</p>
        </section>
        </body>
        </html>""",
        zoneDetectionJobId);
  }
}
