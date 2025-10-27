package app.bpartners.geojobs.service;

import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import jakarta.mail.internet.InternetAddress;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionFinishedMailer implements TriConsumer<String, String, String> {
  public static final String ADMIN_EMAIL = "tech@birdia.fr";
  private final Mailer mailer;

  @Override
  @SneakyThrows
  public void accept(String emailReceiver, String subject, String body) {
    mailer.accept(
        new Email(
            new InternetAddress(emailReceiver),
            List.of(new InternetAddress(ADMIN_EMAIL)),
            List.of(),
            subject,
            body,
            List.of()));
  }
}
