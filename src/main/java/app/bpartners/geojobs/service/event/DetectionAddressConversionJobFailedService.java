package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.FAILED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static java.time.Instant.now;

import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobFailed;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.DetectionAddressConversionTaskRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.DetectionAddressConversionTaskToCsvConverter;
import app.bpartners.geojobs.service.ExcelAddressWriter;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

@Service
public class DetectionAddressConversionJobFailedService
    implements Consumer<DetectionAddressConversionJobFailed> {
  private final DetectionAddressConversionTaskRepository detectionAddressConversionTaskRepository;
  private final Mailer mailer;
  private final HTMLTemplateParser htmlTemplateParser;
  private final BucketComponent bucketComponent;
  private final DetectionRepository detectionRepository;
  private final String adminEmail;
  private final DetectionAddressConversionTaskToCsvConverter csvConverter;
  private final ExcelAddressWriter excelAddressWriter;

  public DetectionAddressConversionJobFailedService(
      DetectionAddressConversionTaskRepository detectionAddressConversionTaskRepository,
      Mailer mailer,
      HTMLTemplateParser htmlTemplateParser,
      BucketComponent bucketComponent,
      DetectionRepository detectionRepository,
      @Value("${admin.email}") String adminEmail,
      DetectionAddressConversionTaskToCsvConverter csvConverter,
      ExcelAddressWriter excelAddressWriter) {
    this.detectionAddressConversionTaskRepository = detectionAddressConversionTaskRepository;
    this.mailer = mailer;
    this.htmlTemplateParser = htmlTemplateParser;
    this.bucketComponent = bucketComponent;
    this.detectionRepository = detectionRepository;
    this.adminEmail = adminEmail;
    this.csvConverter = csvConverter;
    this.excelAddressWriter = excelAddressWriter;
  }

  @SneakyThrows
  @Override
  public void accept(DetectionAddressConversionJobFailed event) {
    var job = event.getJob();
    var failedTasks =
        detectionAddressConversionTaskRepository.findAllByJobIdAndProgressionStatusAndHealthStatus(
            job.getId(), FINISHED.name(), FAILED.name());
    var detection = detectionRepository.findById(job.getDetectionId()).orElseThrow();
    var actualDatetime =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .format(now().atZone(ZoneId.of("Europe/Paris")));

    var emailSubject =
        String.format(
            "BirdIA | Anomalie détectée sur %d adresses - Détection" + " portant l'ID %s le %s",
            failedTasks.size(), detection.getEndToEndId(), actualDatetime);
    var to = new InternetAddress(job.getEmailReceiver());
    var cc = List.of(new InternetAddress(adminEmail));
    var bcc = new ArrayList<InternetAddress>();
    var htmlBody = computeHtmlBody(detection, failedTasks);
    var failedAddressesCsvFile = getFailedAddressesCsvFile(failedTasks, detection);
    var uploadedExcelFile = getExcelUploadedFile(detection);
    var attachments = List.of(failedAddressesCsvFile, uploadedExcelFile);

    mailer.accept(new Email(to, cc, bcc, emailSubject, htmlBody, attachments));
  }

  private File getFailedAddressesCsvFile(
      List<DetectionAddressConversionTask> failedTasks, Detection detection) {
    var fileName = "Adresses non-traitées - Detection " + detection.getEndToEndId();
    return csvConverter.apply(failedTasks, fileName);
  }

  private File getExcelUploadedFile(Detection detection) {
    var excelFileKey = detection.getExcelFileKey();
    File excelUploadedFile;
    if (excelFileKey == null
        && detection.getConvertedAddresses() != null
        && !detection.getConvertedAddresses().isEmpty()) {
      excelUploadedFile = excelAddressWriter.apply(detection.getConvertedAddresses());
    } else {
      excelUploadedFile = bucketComponent.download(excelFileKey);
    }
    var absolutePath = excelUploadedFile.getAbsolutePath();
    var dirIndex = absolutePath.lastIndexOf('/');
    var dirName = absolutePath.substring(0, dirIndex) + "/";
    var filePathWithXlsxExtension =
        dirName + "Adresses soumises - Detection " + detection.getEndToEndId() + ".xlsx";
    var xlsxFileForced = new File(filePathWithXlsxExtension);
    excelUploadedFile.renameTo(xlsxFileForced);
    return xlsxFileForced;
  }

  private String computeHtmlBody(
      Detection detection, List<DetectionAddressConversionTask> failedTasks) {
    var context = new Context();
    context.setVariable("failedTasks", failedTasks);
    context.setVariable("detection", detection);
    return htmlTemplateParser.apply("detection_address_conversion_job_failed", context);
  }
}
