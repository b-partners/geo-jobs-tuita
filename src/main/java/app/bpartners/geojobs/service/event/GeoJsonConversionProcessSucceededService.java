package app.bpartners.geojobs.service.event;

import static java.time.Instant.now;

import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionProcessSucceeded;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.dashboard.DetectionTrackingApi;
import app.bpartners.geojobs.service.dashboard.component.CreateDetectionTracking;
import app.bpartners.geojobs.service.dashboard.component.DetectionInitiator;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeoJsonConversionProcessSucceededService
    implements Consumer<GeoJsonConversionProcessSucceeded> {
  private final DetectionTrackingApi detectionTrackingApi;
  private final CommunityAuthorizationRepository communityAuthorizationRepository;

  @Override
  public void accept(GeoJsonConversionProcessSucceeded event) {
    var detection = event.getDetection();

    detectionTrackingApi.registerDetection(
        getApiKey(detection),
        List.of(
            new CreateDetectionTracking(
                detection.getZoneName(),
                "non supportée",
                now(),
                new DetectionInitiator(
                    "non supporté", detection.getEmailReceiver(), "non supporté"))));
  }

  private String getApiKey(Detection detection) {
    return communityAuthorizationRepository
        .findById(detection.getCommunityOwnerId())
        .map(CommunityAuthorization::getDashboardApiKey)
        .orElseThrow();
  }
}
