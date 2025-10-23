package app.bpartners.geojobs.service.detection;

import static java.time.Instant.now;

import app.bpartners.geojobs.endpoint.rest.model.Prospect;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

@Component
@AllArgsConstructor
public class RoofAnalysisMailer implements BiConsumer<Prospect, File> {
  private final String env = System.getenv("ENV");
  private static final String TEMPLATE_NAME = "roofer_detection_made";
  private final HTMLTemplateParser htmlTemplateParser;
  private final Mailer mailer;
  private final AuthProvider authProvider;

  @SneakyThrows
  @Override
  public void accept(Prospect prospect, File detectionResultPdf) {
    var communityEmailAddress = authProvider.getAuthenticatedCommunity().getEmail();
    var cc = new InternetAddress("tech@birdia.fr");
    var emailBody = computeEmailHtmlBody(prospect);
    var emailSubject =
        String.format(
            "%sAnalyse de la toiture de l’adresse %s",
            !env.equalsIgnoreCase("prod") ? "[" + env + "] " : "", prospect.getAddress());
    var attachments = List.of(detectionResultPdf);

    mailer.accept(
        new Email(
            new InternetAddress(communityEmailAddress),
            List.of(cc),
            List.of(),
            emailSubject,
            emailBody,
            attachments));
  }

  private String computeEmailHtmlBody(Prospect prospect) {
    var context = new Context();
    context.setVariable("firstName", prospect.getFirstName());
    context.setVariable("lastName", prospect.getLastName());
    context.setVariable("phoneNumber", prospect.getPhone());
    context.setVariable("email", prospect.getEmail());
    context.setVariable("address", prospect.getAddress());
    context.setVariable(
        "creationDatetime",
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .format(now().atZone(ZoneId.of("Europe/Paris"))));
    return htmlTemplateParser.apply(TEMPLATE_NAME, context);
  }
}
