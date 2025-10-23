package app.bpartners.geojobs.service;

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblySucceeded;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.ZoneDetectionJobRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.service.event.DetectionSucceededService;
import app.bpartners.geojobs.service.event.GeoJsonConversionAssemblySucceededService;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeoJsonConversionAssemblySucceededServiceTest {
  private static final String HTTP_LOCALHOST_PRESIGNED_URL = "http://localhost/presigned-url";
  DetectionFinishedMailer detectionFinishedMailerMock = mock();
  HTMLTemplateParser htmlTemplateParser = new HTMLTemplateParser();
  DetectionRepository detectionRepositoryMock = mock(DetectionRepository.class);
  BucketComponent bucketComponentMock = mock(BucketComponent.class);
  EventProducer eventProducerMock = mock(EventProducer.class);

  ZoneDetectionJobRepository zoneDetectionJobRepositoryMock =
      mock(ZoneDetectionJobRepository.class);
  GeoJsonConversionAssemblySucceededService subject =
      new GeoJsonConversionAssemblySucceededService(
          new DetectionSucceededService(
              detectionFinishedMailerMock,
              htmlTemplateParser,
              detectionRepositoryMock,
              bucketComponentMock,
              zoneDetectionJobRepositoryMock),
          eventProducerMock,
          detectionRepositoryMock);
  private final String zoneDetectionJobId = randomUUID().toString();
  private final String detectionE2Id = randomUUID().toString();

  @BeforeEach
  void setUp() {
    var geoJsonFileKey = randomUUID().toString();
    var zoneDetectionJob = mock(ZoneDetectionJob.class);
    when(zoneDetectionJob.getId()).thenReturn(zoneDetectionJobId);
    when(zoneDetectionJobRepositoryMock.findById(any())).thenReturn(Optional.of(zoneDetectionJob));

    when(bucketComponentMock.presign(geoJsonFileKey)).thenReturn(HTTP_LOCALHOST_PRESIGNED_URL);
    var detectionMock = mock(Detection.class);
    when(detectionMock.getEndToEndId()).thenReturn(detectionE2Id);
    when(detectionMock.getGeojsonS3FileKey()).thenReturn(geoJsonFileKey);
    when(detectionMock.isSynchronous()).thenReturn(false);
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));
  }

  @Test
  void trigger_detection_finished_mailer_from_geo_json_conversion_succeeded() {
    var succeededGeoJsonConversionJobMock = mock(GeoJsonConversionJob.class);
    var jobStatusMock = mock(JobStatus.class);
    var creationDatetime = now();
    var emailReceiver = "emailReceiver";
    var zoneName = "zoneName";
    var geoJsonSucceededDatetime =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .format(creationDatetime.atZone(ZoneId.of("Europe/Paris")));
    var emailSubject =
        String.format("Analyse sur la zone %s terminée le %s", zoneName, geoJsonSucceededDatetime);
    when(jobStatusMock.getCreationDatetime()).thenReturn(creationDatetime);
    when(succeededGeoJsonConversionJobMock.getZoneDetectionJobId()).thenReturn(zoneDetectionJobId);
    when(succeededGeoJsonConversionJobMock.getStatus()).thenReturn(jobStatusMock);
    when(succeededGeoJsonConversionJobMock.getEmailReceiver()).thenReturn(emailReceiver);
    when(succeededGeoJsonConversionJobMock.getZoneName()).thenReturn(zoneName);

    assertDoesNotThrow(
        () ->
            subject.accept(
                new GeoJsonConversionAssemblySucceeded(succeededGeoJsonConversionJobMock)));

    var stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(detectionFinishedMailerMock, only())
        .accept(eq(emailReceiver), eq(emailSubject), stringCaptor.capture());
    var emailBodyCaptured = stringCaptor.getValue();
    assertEquals(expectedEmailBody(geoJsonSucceededDatetime), emailBodyCaptured);
  }

  @Test
  void do_not_trigger_detection_finished_mailer_when_detection_sync() {
    reset(detectionRepositoryMock);
    var detectionMock = mock(Detection.class);
    when(detectionMock.getEndToEndId()).thenReturn(detectionE2Id);
    when(detectionMock.isSynchronous()).thenReturn(true);
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));

    var succeededGeoJsonConversionJobMock = mock(GeoJsonConversionJob.class);
    var jobStatusMock = mock(JobStatus.class);
    var creationDatetime = now();
    var emailReceiver = "emailReceiver";
    var zoneName = "zoneName";
    when(jobStatusMock.getCreationDatetime()).thenReturn(creationDatetime);
    when(succeededGeoJsonConversionJobMock.getZoneDetectionJobId()).thenReturn(zoneDetectionJobId);
    when(succeededGeoJsonConversionJobMock.getStatus()).thenReturn(jobStatusMock);
    when(succeededGeoJsonConversionJobMock.getEmailReceiver()).thenReturn(emailReceiver);
    when(succeededGeoJsonConversionJobMock.getZoneName()).thenReturn(zoneName);

    assertDoesNotThrow(
        () ->
            subject.accept(
                new GeoJsonConversionAssemblySucceeded(succeededGeoJsonConversionJobMock)));

    verify(detectionFinishedMailerMock, never()).accept(anyString(), anyString(), anyString());
  }

  private @NotNull String expectedEmailBody(String geoJsonSucceededDatetime) {
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
        <p>Le traitement de la détection portant l'ID <span>%s</span> s'est terminée avec succès le <span>%s</span>.</p>
        <p>Vous pouvez télécharger le résultat de la détection au format geojson <a href="%s">ici</a>.</p>
    </div>
   \s
    <p>Cordialement,</p>
    <p>L'équipe BirdIA.</p>
</section>
</body>
</html>""",
        detectionE2Id, geoJsonSucceededDatetime, HTTP_LOCALHOST_PRESIGNED_URL);
  }
}
