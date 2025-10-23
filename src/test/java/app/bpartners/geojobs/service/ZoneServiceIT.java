package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.MACHINE_DETECTION;
import static app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.SUCCEEDED;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.rest.model.DetectionStep;
import app.bpartners.geojobs.endpoint.rest.model.Status;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.*;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.utils.FeatureCreator;
import app.bpartners.geojobs.utils.detection.DetectionCreator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class ZoneServiceIT extends FacadeIT {
  @MockBean BucketComponent bucketComponent;
  @MockBean DetectionFeaturesResultImageRetriever featuresImageRetriever;
  @MockBean DetectionImageAttributeRetriever imageAttributeRetriever;
  @MockBean DetectionVggAttributeRetriever vggAttributeRetriever;

  @Autowired DetectionRepository detectionRepository;
  @Autowired DetectionStepRepository detectionStepRepository;
  DetectionCreator detectionCreator;

  @Autowired ZoneService subject;
  @Autowired private ZoneTilingJobRepository zoneTilingJobRepository;
  @Autowired private ZoneDetectionJobRepository zoneDetectionJobRepository;
  @MockBean AuthProvider authProviderMock;
  @MockBean CommunityAuthorizationRepository communityAuthorizationRepository;

  @BeforeEach
  void setUp() {
    detectionCreator = new DetectionCreator(new FeatureCreator());
  }

  @Test
  void update_detection_step() {
    var principalMock = mock(Principal.class);
    var apiKey = randomUUID().toString();
    var communityAuthorization = mock(CommunityAuthorization.class);

    when(communityAuthorization.getId()).thenReturn(null);
    when(communityAuthorizationRepository.findByApiKey(apiKey))
        .thenReturn(Optional.of(communityAuthorization));
    when(principalMock.getApiKey()).thenReturn(apiKey);
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);
    var detectionId = randomUUID().toString();
    var ztjId = randomUUID().toString();
    var zdjId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, ztjId, zdjId);
    var zoneTilingJob =
        new ZoneTilingJob()
            .toBuilder()
                .id(ztjId)
                .zoneName("dummyZoneName")
                .emailReceiver("dummyEmailReceiver")
                .submissionInstant(Instant.now())
                .build();
    var zoneDetectionJob = new ZoneDetectionJob().toBuilder().id(zdjId).build();
    zoneDetectionJobRepository.save(zoneDetectionJob);
    zoneTilingJobRepository.save(zoneTilingJob);
    detectionRepository.save(detection);

    var restStep =
        new DetectionStep()
            .name(MACHINE_DETECTION)
            .status(new Status().progression(Status.ProgressionEnum.FINISHED).health(SUCCEEDED));
    when(bucketComponent.presign(any())).thenReturn(null);
    when(featuresImageRetriever.apply(any())).thenReturn(List.of());
    when(imageAttributeRetriever.apply(any())).thenReturn(null);
    when(vggAttributeRetriever.apply(any())).thenReturn(null);

    var actual = subject.updateDetectionStep(detectionId, null, restStep);
    var savedSteps = detectionStepRepository.findAll();

    assertFalse(savedSteps.isEmpty());
    assertNotNull(actual.getStep());
    assertEquals(restStep.getName(), actual.getStep().getName());
    assertEquals(
        restStep.getStatus().getProgression(), actual.getStep().getStatus().getProgression());
    assertEquals(restStep.getStatus().getHealth(), actual.getStep().getStatus().getHealth());
  }
}
