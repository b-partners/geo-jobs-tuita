package app.bpartners.geojobs.service.dashboard;

import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.service.dashboard.component.CreateDetectionTracking;
import app.bpartners.geojobs.service.dashboard.component.DetectionInitiator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

@Disabled("TODO: local use only, disable otherwise")
@Slf4j
class DetectionTrackingApiIT {
  private final String apiKey = System.getenv("API_KEY");
  final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  final ApiConfiguration apiConfiguration =
      new ApiConfiguration(System.getenv("BPARTNERS_API_URL"));
  final RestTemplate restTemplate = new RestTemplate();
  DetectionTrackingApi subject =
      new DetectionTrackingApi(
          apiConfiguration, new SecurityApi(restTemplate, apiConfiguration, objectMapper));

  @BeforeEach
  void setUp() {
    var principalMock = mock(Principal.class);
    when(principalMock.getApiKey()).thenReturn(apiKey);
  }

  @Test
  void register_detection() {
    var actual =
        subject.registerDetection(
            apiKey,
            List.of(
                new CreateDetectionTracking(
                    "zone from geo-jobs",
                    "address from geo-jobs",
                    now(),
                    new DetectionInitiator("Ryan", "ryan@email.com", "0611223344"))));

    assertNotNull(actual);
    log.info(actual.toString());
  }
}
