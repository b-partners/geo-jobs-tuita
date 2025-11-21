package app.bpartners.geojobs.service.event;

import static java.time.Instant.now;
import static java.util.Objects.requireNonNullElse;

import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.endpoint.rest.model.GeoServerProperties;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.GeoServerParameterStringMapValue;
import app.bpartners.geojobs.service.detection.DetectionGeoServerParameterModelMapper;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

@Service
@AllArgsConstructor
public class DetectionSavedService implements Consumer<DetectionSaved> {
  public static final String DETECTION_SAVED_TEMPLATE = "detection_saved";
  private final Mailer mailer;
  private final BucketComponent bucketComponent;
  private final DetectionGeoServerParameterModelMapper detectionGeoServerParameterModelMapper;
  private final CommunityAuthorizationRepository communityAuthorizationRepository;
  private final DetectionRepository detectionRepository;

  @SneakyThrows
  @Override
  public void accept(DetectionSaved detectionSaved) {
    var detectionIdentifier = detectionSaved.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    List<InternetAddress> cc = List.of();
    List<InternetAddress> bcc = List.of();
    var env = System.getenv("ENV");
    String subject =
        String.format(
            "[%s] Detection(e2Id=%s, communityOwnerId=%s) modifiée le %s",
            env == null ? "" : env.toLowerCase(),
            detection.getEndToEndId(),
            detection.getCommunityOwnerId(),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                .format(now().atZone(ZoneId.of("Europe/Paris"))));
    String htmlBody =
        computeStaticEmailBody(
            detection,
            bucketComponent,
            detectionGeoServerParameterModelMapper,
            communityAuthorizationRepository);
    List<File> attachments = List.of();
    mailer.accept(
        new Email(new InternetAddress("tech@birdia.fr"), cc, bcc, subject, htmlBody, attachments));
  }

  @NonNull
  public static String computeStaticEmailBody(
      Detection detection,
      BucketComponent bucketComponent,
      DetectionGeoServerParameterModelMapper detectionGeoServerParameterModelMapper,
      CommunityAuthorizationRepository communityAuthorizationRepository) {
    var shapeFilePresignURL = getShapeFilePresignURL(detection, bucketComponent);
    var excelFilePresignURL = getExcelFilePresignURL(detection, bucketComponent);
    var detectableObjectModel = detection.getDetectableObjectModel();
    var geoServerProperties = detection.getGeoServerProperties();
    var geoServerParameter =
        getGeoServerParameter(detectionGeoServerParameterModelMapper, geoServerProperties);
    var geoServerUrl = geoServerProperties == null ? null : geoServerProperties.getGeoServerUrl();

    var htmlTemplateParser = new HTMLTemplateParser();
    Context context = new Context();
    context.setVariable("detectableObjectModel", detectableObjectModel);
    context.setVariable(
        "geoServerParameterStringMapValues", requireNonNullElse(geoServerParameter, List.of()));
    context.setVariable("geoServerUrl", geoServerUrl);
    context.setVariable("detection", detection);
    context.setVariable("shapeFileUrl", shapeFilePresignURL);
    context.setVariable("excelFileUrl", excelFilePresignURL);
    context.setVariable(
        "community",
        communityAuthorizationRepository.findById(detection.getCommunityOwnerId()).orElse(null));

    return htmlTemplateParser.apply(DETECTION_SAVED_TEMPLATE, context);
  }

  private static List<GeoServerParameterStringMapValue> getGeoServerParameter(
      DetectionGeoServerParameterModelMapper detectionGeoServerParameterModelMapper,
      GeoServerProperties geoServerProperties) {
    return geoServerProperties == null
        ? null
        : detectionGeoServerParameterModelMapper.apply(geoServerProperties.getGeoServerParameter());
  }

  private static String getExcelFilePresignURL(
      Detection detection, BucketComponent bucketComponent) {
    return detection.getExcelFileKey() == null
        ? null
        : bucketComponent.presign(detection.getExcelFileKey(), Duration.ofHours(24L)).toString();
  }

  private static String getShapeFilePresignURL(
      Detection detection, BucketComponent bucketComponent) {
    return detection.getShapeFileKey() == null
        ? null
        : bucketComponent.presign(detection.getShapeFileKey(), Duration.ofHours(24L)).toString();
  }
}
