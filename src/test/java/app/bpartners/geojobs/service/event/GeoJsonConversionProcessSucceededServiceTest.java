package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionProcessSucceeded;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.dashboard.DetectionTrackingApi;
import app.bpartners.geojobs.service.dashboard.component.CreateDetectionTracking;
import app.bpartners.geojobs.service.dashboard.component.DetectionInitiator;
import app.bpartners.geojobs.service.dashboard.component.DetectionTracking;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeoJsonConversionProcessSucceededServiceTest {
  DetectionTrackingApi detectionTrackingApiMock = mock();
  CommunityAuthorizationRepository communityAuthorizationRepositoryMock = mock();
  GeoJsonConversionProcessSucceededService subject =
      new GeoJsonConversionProcessSucceededService(
          detectionTrackingApiMock, communityAuthorizationRepositoryMock);

  final String apiKey = "apiKey";

  @BeforeEach
  void setUp() {
    when(detectionTrackingApiMock.registerDetection(eq(apiKey), any()))
        .thenReturn(List.of(mock(DetectionTracking.class)));
  }

  @Test
  void register_detection_ok() {
    var communityOwnerId = randomUUID().toString();
    var zoneName = "detection zone name";
    var emailReceiver = "detection email receiver";

    var detectionMock = mock(Detection.class);
    when(detectionMock.getZoneName()).thenReturn(zoneName);
    when(detectionMock.getEmailReceiver()).thenReturn(emailReceiver);
    when(detectionMock.getCommunityOwnerId()).thenReturn(communityOwnerId);

    when(communityAuthorizationRepositoryMock.findById(communityOwnerId))
        .thenReturn(Optional.of(CommunityAuthorization.builder().dashboardApiKey(apiKey).build()));

    assertDoesNotThrow(() -> subject.accept(new GeoJsonConversionProcessSucceeded(detectionMock)));

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(detectionTrackingApiMock, only()).registerDetection(eq(apiKey), listCaptor.capture());
    var actualCreateDetection = (List<CreateDetectionTracking>) listCaptor.getValue();
    var expectedCreateDetection =
        getExpectedCreateDetection(zoneName, emailReceiver, actualCreateDetection);

    assertEquals(expectedCreateDetection, actualCreateDetection);
  }

  private @NotNull List<CreateDetectionTracking> getExpectedCreateDetection(
      String zoneName, String emailReceiver, List<CreateDetectionTracking> actualCreateDetection) {
    return List.of(
        new CreateDetectionTracking(
            zoneName,
            "non supportée",
            actualCreateDetection.getFirst().creationDatetime(),
            new DetectionInitiator("non supporté", emailReceiver, "non supporté")));
  }

  @Test
  void register_detection_fail_on_api_failure() {
    var detectionMock = mock(Detection.class);
    when(detectionMock.getCommunityOwnerId()).thenReturn(randomUUID().toString());
    reset(detectionTrackingApiMock);
    when(communityAuthorizationRepositoryMock.findById(any()))
        .thenReturn(Optional.of(CommunityAuthorization.builder().apiKey(apiKey).build()));
    when(detectionTrackingApiMock.registerDetection(any(), any()))
        .thenThrow(new RuntimeException());

    assertThrows(
        RuntimeException.class,
        () -> subject.accept(new GeoJsonConversionProcessSucceeded(detectionMock)));
  }

  @Test
  void register_detection_fail_on_api_key_not_found() {
    var detectionMock = mock(Detection.class);
    when(detectionMock.getCommunityOwnerId()).thenReturn(randomUUID().toString());
    when(communityAuthorizationRepositoryMock.findById(any())).thenReturn(Optional.empty());

    assertThrows(
        RuntimeException.class,
        () -> subject.accept(new GeoJsonConversionProcessSucceeded(detectionMock)));
  }
}
