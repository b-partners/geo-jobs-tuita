package app.bpartners.geojobs.service.event;

import static java.math.RoundingMode.UP;
import static java.util.Locale.FRANCE;

import app.bpartners.geojobs.endpoint.event.model.DetectionAreaUnsupported;
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
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

@Service
@AllArgsConstructor
public class DetectionAreaUnsupportedService implements Consumer<DetectionAreaUnsupported> {
  private static final String DETECTION_AREA_UNSUPPORTED_TEMPLATE = "detection_area_unsupported";
  private final DetectionRepository detectionRepository;
  private final CommunityAuthorizationRepository communityAuthorizationRepository;
  private final Mailer mailer;
  private final HTMLTemplateParser htmlTemplateParser;

  @SneakyThrows
  @Override
  public void accept(DetectionAreaUnsupported detectionAreaUnsupported) {
    var detection =
        detectionRepository
            .findById(detectionAreaUnsupported.getDetectionIdentifier())
            .orElseThrow();
    var communityOwner =
        communityAuthorizationRepository.findById(detection.getCommunityOwnerId()).orElseThrow();
    var emailSubject =
        String.format(
            "Surface de la détection (id=%s) lancée par %s excédant 1 km^2",
            detection.getEndToEndId(), communityOwner.getName());
    var computedArea = detectionAreaUnsupported.getComputedArea();
    var htmlBody = emailBody(detection, communityOwner, computedArea);
    var attachments =
        List.of(
            providedFeaturesAsFile(
                detection.getEndToEndId(), detection.getProvidedGeoJsonZone().stream().toList()));

    mailer.accept(
        new Email(
            new InternetAddress("bpartners.artisans@gmail.com"),
            List.of(new InternetAddress("tech@birdia.fr")),
            List.of(),
            emailSubject,
            htmlBody,
            attachments));
  }

  private String emailBody(
      Detection detection, CommunityAuthorization communityOwner, Double computedArea) {
    var context = new Context();
    context.setVariable("detection", detection);
    context.setVariable("communityOwner", communityOwner);
    context.setVariable("computedAreaInM2", formatAreaWithWhiteSpace(computedArea));
    context.setVariable(
        "computedAreaInKm2",
        BigDecimal.valueOf(computedArea / 1_000_000.0).setScale(2, UP).doubleValue());
    context.setVariable("detectionNeedsImageOutput", detection.needsImageOutput() ? "Oui" : "Non");
    context.setVariable("detectionOutputType", detection.isOutputZipped() ? "ZIP" : "GeoJson");
    return htmlTemplateParser.apply(DETECTION_AREA_UNSUPPORTED_TEMPLATE, context);
  }

  private String formatAreaWithWhiteSpace(Double computedArea) {
    var roundedValue = BigDecimal.valueOf(computedArea).setScale(0, UP).longValue();
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(FRANCE);
    symbols.setGroupingSeparator('\u00A0');
    DecimalFormat df = new DecimalFormat("#,###", symbols);
    return df.format(roundedValue);
  }

  @SneakyThrows
  private File providedFeaturesAsFile(String detectionE2Id, List<Feature> features) {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    File tempFile = File.createTempFile("detection_" + detectionE2Id, ".json");
    mapper.writeValue(tempFile, features);
    return tempFile;
  }
}
