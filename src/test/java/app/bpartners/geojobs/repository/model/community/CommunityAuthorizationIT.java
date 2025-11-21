package app.bpartners.geojobs.repository.model.community;

import static app.bpartners.geojobs.endpoint.rest.security.authenticator.ApiKeyAuthenticator.API_KEY_HEADER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.rest.api.DetectionApi;
import app.bpartners.geojobs.endpoint.rest.api.MachineDetectionApi;
import app.bpartners.geojobs.endpoint.rest.client.ApiClient;
import app.bpartners.geojobs.endpoint.rest.client.ApiException;
import app.bpartners.geojobs.endpoint.rest.model.CreateDetection;
import app.bpartners.geojobs.endpoint.rest.security.authorizer.DetectionAuthorizer;
import app.bpartners.geojobs.endpoint.rest.security.model.Authority;
import app.bpartners.geojobs.endpoint.rest.validator.CreateDetectionValidator;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.SurfaceUnit;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.detection.DetectionCreationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

class CommunityAuthorizationIT extends FacadeIT {

  MachineDetectionApi machineDetectionApi;
  DetectionApi detectionApi;

  @Autowired ObjectMapper om;

  @MockBean CreateDetectionValidator createDetectionValidatorMock;
  @MockBean DetectionAuthorizer detectionAuthorizerMock;
  @MockBean DetectionCreationMapper detectionCreationMapperMock;
  @MockBean EventProducer eventProducerMock;

  @LocalServerPort private int port;

  @Autowired CommunityAuthorizationRepository communityAuthorizationRepository;

  @BeforeEach
  void setUp() {
    communityAuthorizationRepository.save(communityAuthorization());
    doNothing().when(createDetectionValidatorMock).accept(any());
    doNothing().when(eventProducerMock).accept(any());
    doNothing().when(detectionAuthorizerMock).accept(anyString(), any(), any());
  }

  @Test
  void process_detection_with_valid_api_key() throws ApiException {
    setupClientWithValidApiKey();

    var detectionID = UUID.randomUUID().toString();
    when(detectionCreationMapperMock.apply(
            any(CreateDetection.class), anyString(), anyString(), anyBoolean()))
        .thenReturn(detection(detectionID));

    var actual = detectionApi.processDetection(detectionID, createDetection());

    assertEquals(detection(detectionID).getId(), actual.getId());
  }

  @Test
  void process_detection_with_valid_dashboard_api_key() throws ApiException {
    setupClientWithDashboardApiKey();

    var detectionID = UUID.randomUUID().toString();
    when(detectionCreationMapperMock.apply(
            any(CreateDetection.class), anyString(), anyString(), anyBoolean()))
        .thenReturn(detection(detectionID));

    var actual = detectionApi.processDetection(detectionID, createDetection());

    assertEquals(detection(detectionID).getId(), actual.getId());
  }

  @Test
  void process_detection_with_invalid_api_key() {
    setupClientWithInvalidApiKey();
    var detectionID = UUID.randomUUID().toString();

    var exception =
        assertThrows(
            ApiException.class,
            () -> detectionApi.processDetection(detectionID, createDetection()));

    assertTrue(exception.getMessage().contains("403"));
    assertTrue(exception.getMessage().contains("FORBIDDEN"));
  }

  private static Detection detection(String detectionId) {
    return new Detection().toBuilder().id(detectionId).endToEndId(detectionId).build();
  }

  private static CreateDetection createDetection() {
    return new CreateDetection();
  }

  void setupClientWithDashboardApiKey() {
    setupClient("$dashboardApiKey$");
  }

  void setupClientWithValidApiKey() {
    setupClient("api_key_1");
  }

  void setupClientWithInvalidApiKey() {
    setupClient("invalid_api_key");
  }

  void setupClient(String apiKey) {
    var authenticatedClient = new ApiClient();
    authenticatedClient.setRequestInterceptor(builder -> builder.header(API_KEY_HEADER, apiKey));
    authenticatedClient.setScheme("http");
    authenticatedClient.setHost("localhost");
    authenticatedClient.setPort(port);
    authenticatedClient.setObjectMapper(om);

    detectionApi = new DetectionApi(authenticatedClient);
    machineDetectionApi = new MachineDetectionApi(authenticatedClient);
  }

  private static CommunityAuthorization communityAuthorization() {

    return CommunityAuthorization.builder()
        .authorizedZones(List.of())
        .id("community_id")
        .email("co@mail.example")
        .name("Random Community")
        .dashboardApiKey("$dashboardApiKey$")
        .role(Authority.Role.ROLE_COMMUNITY)
        .maxSurfaceUnit(SurfaceUnit.SQUARE_DEGREE)
        .apiKeys(
            List.of(
                CommunityAuthorizationApiKey.builder()
                    .communityOwnerId("community_id")
                    .keyValue("api_key_1")
                    .id("c1_k1")
                    .build(),
                CommunityAuthorizationApiKey.builder()
                    .communityOwnerId("community_id")
                    .keyValue("api_key_2")
                    .id("c1_k2")
                    .build()))
        .build();
  }
}
