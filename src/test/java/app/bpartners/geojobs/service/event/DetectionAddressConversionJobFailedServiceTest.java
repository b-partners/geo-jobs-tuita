package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobFailed;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.DetectionAddressConversionTaskRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionJob;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.DetectionAddressConversionTaskToCsvConverter;
import app.bpartners.geojobs.service.ExcelAddressWriter;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DetectionAddressConversionJobFailedServiceTest {
  private static final String ADMIN_EMAIL = "admin@email.com";
  DetectionAddressConversionTaskRepository taskRepositoryMock = mock();
  Mailer mailerMock = mock();
  HTMLTemplateParser htmlTemplateParser = new HTMLTemplateParser();
  BucketComponent bucketComponentMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  DetectionAddressConversionTaskToCsvConverter taskToCsvConverterMock = mock();
  ExcelAddressWriter excelAddressWriterMock = mock();
  DetectionAddressConversionJobFailedService subject =
      new DetectionAddressConversionJobFailedService(
          taskRepositoryMock,
          mailerMock,
          htmlTemplateParser,
          bucketComponentMock,
          detectionRepositoryMock,
          ADMIN_EMAIL,
          taskToCsvConverterMock,
          excelAddressWriterMock);

  @SneakyThrows
  @Test
  void mail_failed_addresses() {
    var jobId = randomUUID().toString();
    var detectionId = randomUUID().toString();
    var detectionE2Id = randomUUID().toString();
    var emailReceiver = "dummy@email.com";
    var excelFileKey = randomUUID().toString();
    var jobMock = mock(DetectionAddressConversionJob.class);
    var taskMock = mock(DetectionAddressConversionTask.class);
    var detectionMock = mock(Detection.class);
    var failedAddressesCsvFileMock = mock(File.class);
    var tmpFileForExcelFile = File.createTempFile(excelFileKey, ".txt");

    when(jobMock.getId()).thenReturn(jobId);
    when(jobMock.getDetectionId()).thenReturn(detectionId);
    when(detectionMock.getEndToEndId()).thenReturn(detectionE2Id);
    when(jobMock.getEmailReceiver()).thenReturn(emailReceiver);
    when(detectionMock.getExcelFileKey()).thenReturn(excelFileKey);
    when(taskMock.getAddress()).thenReturn("Malformed address");
    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detectionMock));
    when(taskRepositoryMock.findAllByJobIdAndProgressionStatusAndHealthStatus(
            jobId, "FINISHED", "FAILED"))
        .thenReturn(List.of(taskMock));
    when(bucketComponentMock.download(excelFileKey)).thenReturn(tmpFileForExcelFile);
    when(taskToCsvConverterMock.apply(eq(List.of(taskMock)), anyString()))
        .thenReturn(failedAddressesCsvFileMock);

    assertDoesNotThrow(() -> subject.accept(new DetectionAddressConversionJobFailed(jobMock)));

    var emailCaptor = ArgumentCaptor.forClass(Email.class);
    verify(mailerMock, only()).accept(emailCaptor.capture());
    var email = emailCaptor.getValue();
    var attachments = email.attachments();
    verify(taskToCsvConverterMock)
        .apply(eq(List.of(taskMock)), eq("Adresses non-traitées - Detection " + detectionE2Id));
    assertEquals(new InternetAddress(emailReceiver), email.to());
    assertEquals(List.of(new InternetAddress(ADMIN_EMAIL)), email.cc());
    assertEquals(List.of(), email.bcc());
    assertTrue(
        email
            .subject()
            .contains(
                "BirdIA | Anomalie détectée sur 1 adresses - Détection"
                    + " portant l'ID "
                    + detectionE2Id
                    + " le "));
    assertEquals(expectedHtmlBody(detectionE2Id), email.htmlBody());
    assertTrue(attachments.contains(failedAddressesCsvFileMock));
    assertTrue(
        attachments.stream()
            .anyMatch(
                file -> {
                  if (!mockingDetails(file).isMock()) {
                    return file.getAbsolutePath()
                        .contains("Adresses soumises - Detection " + detectionE2Id);
                  }
                  return false;
                }));
  }

  private static @NotNull String expectedHtmlBody(String detectionE2Id) {
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
    <p>Lors du traitement de la détection de toiture portant l'identifiant <strong>%s</strong>,
        <span>1</span> adresses n'ont pas pas pu être traitées correctement.</p>
    <p>Ces adresses non traitées, ainsi que le fichier excel initialement soumis, sont disponibles en pièces jointes de
        ce mail.</p>
    <p>Cordialement.</p>
    <p>L'équipe BirdIA.</p>
</section>
</body>
</html>""",
        detectionE2Id);
  }
}
