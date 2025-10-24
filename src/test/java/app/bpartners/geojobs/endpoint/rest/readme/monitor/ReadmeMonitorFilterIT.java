package app.bpartners.geojobs.endpoint.rest.readme.monitor;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionSurfaceUnit.SQUARE_METER;
import static app.bpartners.geojobs.endpoint.rest.security.authenticator.ApiKeyAuthenticator.API_KEY_HEADER;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_COMMUNITY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.rest.api.UsageApi;
import app.bpartners.geojobs.endpoint.rest.client.ApiClient;
import app.bpartners.geojobs.endpoint.rest.client.ApiException;
import app.bpartners.geojobs.endpoint.rest.model.DetectionUsage;
import app.bpartners.geojobs.endpoint.rest.readme.monitor.factory.ReadmeLogFactory;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.service.CommunityUsedSurfaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

class ReadmeMonitorFilterIT extends FacadeIT {
  @LocalServerPort private int port;
  @Autowired ObjectMapper objectMapper;
  @MockBean CommunityUsedSurfaceService communityUsedSurfaceServiceMock;
  @MockBean CommunityAuthorizationRepository communityAuthRepositoryMock;
  @MockBean ReadmeLogFactory readmeLogFactoryMock;
  @MockBean EventProducer eventProducerMock;
  UsageApi usageApi;
  DetectionUsage expectedDetectionUsage = new DetectionUsage();
  CommunityAuthorization communityAuthorizationMock = mock();

  @BeforeEach
  void setup() {
    var authenticatedClient = new ApiClient();
    authenticatedClient.setRequestInterceptor(
        builder -> builder.header(API_KEY_HEADER, "communityApiKey"));
    authenticatedClient.setScheme("http");
    authenticatedClient.setHost("localhost");
    authenticatedClient.setPort(port);
    authenticatedClient.setObjectMapper(objectMapper);

    usageApi = new UsageApi(authenticatedClient);
    when(communityAuthRepositoryMock.findByApiKey(any()))
        .thenReturn(Optional.of(communityAuthorizationMock));
    when(communityAuthorizationMock.isApiKeyRevoked()).thenReturn(false);
    when(communityAuthorizationMock.getRole()).thenReturn(ROLE_COMMUNITY);
    when(readmeLogFactoryMock.createReadmeLog(any(), any(), any(), any(), any(), any()))
        .thenReturn(mock());
    doNothing().when(eventProducerMock).accept(any());
  }

  @Test
  void can_save_log_with_request_ok() throws ApiException {
    when(communityUsedSurfaceServiceMock.getUsage(any(), any())).thenReturn(expectedDetectionUsage);
    var actual = usageApi.getDetectionUsage(SQUARE_METER);

    assertEquals(expectedDetectionUsage, actual);
    verify(eventProducerMock, times(1)).accept(any());
  }

  @Test
  void can_save_log_with_request_ko() {
    when(communityUsedSurfaceServiceMock.getUsage(any(), any()))
        .thenThrow(BadRequestException.class);

    var error = assertThrows(ApiException.class, () -> usageApi.getDetectionUsage(SQUARE_METER));
    assertTrue(error.getMessage().contains("400 BAD_REQUEST"));
    verify(eventProducerMock, times(1)).accept(any());
  }
}
