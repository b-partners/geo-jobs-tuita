package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.model.DetectionModelUnsupported;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class DetectionModelUnsupportedService implements Consumer<DetectionModelUnsupported> {
  private static final String DETECTION_MODEL_UNSUPPORTED_TEMPLATE = "detection_model_unsupported";
  private final DetectionRepository detectionRepository;
  private final CommunityAuthorizationRepository communityAuthorizationRepository;
  private final HTMLTemplateParser htmlTemplateParser;
  private final Mailer mailer;

  @SneakyThrows
  @Override
  public void accept(DetectionModelUnsupported event) {
    var detection = detectionRepository.findById(event.getDetectionIdentifier()).orElseThrow();
    var communityOwner =
        communityAuthorizationRepository.findById(detection.getCommunityOwnerId()).orElseThrow();
    var emailSubject =
        String.format(
            "Détection (e2Id=%s) lancée par %s sur le modèle %s",
            detection.getEndToEndId(),
            communityOwner.getName(),
            detection.getDetectableObjectModel().getModelName());
    var attachments =
        List.of(
            providedFeaturesAsFile(
                detection.getEndToEndId(), detection.getProvidedGeoJsonZone().stream().toList()));
    var htmlBody = emailBody(detection, communityOwner);

    mailer.accept(
        new Email(
            new InternetAddress("bpartners.artisans@gmail.com"),
            List.of(new InternetAddress("tech@birdia.fr")),
            List.of(),
            emailSubject,
            htmlBody,
            attachments));
  }

  private String emailBody(Detection detection, CommunityAuthorization communityOwner) {
    var context = new Context();
    context.setVariable("detection", detection);
    context.setVariable("communityOwner", communityOwner);
    context.setVariable("detectionNeedsImageOutput", detection.needsImageOutput() ? "Oui" : "Non");
    context.setVariable("detectionOutputType", detection.isOutputZipped() ? "ZIP" : "GeoJson");
    return htmlTemplateParser.apply(DETECTION_MODEL_UNSUPPORTED_TEMPLATE, context);
  }

  @SneakyThrows
  private File providedFeaturesAsFile(String detectionE2Id, List<Feature> features) {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    File tempFile = File.createTempFile("detection_" + detectionE2Id, ".geojson");
    mapper.writeValue(tempFile, features);
    return tempFile;
  }
}
