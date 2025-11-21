package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.TILING;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.service.event.DetectionStepUpdatedService.computeStaticDetectionStepUpdateEmailBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.DetectionStepUpdated;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DetectionStepUpdatedServiceTest {
  Mailer mailerMock = mock();
  DetectionStepUpdatedService subject = new DetectionStepUpdatedService(mailerMock);

  @Test
  void accept_ok() throws AddressException {
    var detection =
        Detection.builder()
            .id("id")
            .endToEndId("endToEndId")
            .communityOwnerId("communityOwnerId")
            .detectionSteps(List.of(DetectionStep.builder().name(TILING).health(SUCCEEDED).build()))
            .emailReceiver("emailReceiver")
            .build();

    subject.accept(DetectionStepUpdated.builder().detection(detection).build());

    String htmlBody = computeStaticDetectionStepUpdateEmailBody(detection);
    var emailCaptor = ArgumentCaptor.forClass(Email.class);
    verify(mailerMock, only()).accept(emailCaptor.capture());
    var actualEmail = emailCaptor.getValue();
    var expectedMail =
        new Email(
            new InternetAddress(detection.getEmailReceiver()),
            List.of(new InternetAddress("tech@birdia.fr")),
            List.of(),
            actualEmail.subject(),
            htmlBody,
            List.of());
    assertEquals(expectedMail, actualEmail);
  }
}
