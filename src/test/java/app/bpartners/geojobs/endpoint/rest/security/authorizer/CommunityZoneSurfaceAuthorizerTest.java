package app.bpartners.geojobs.endpoint.rest.security.authorizer;

import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityUsedSurface;
import app.bpartners.geojobs.service.CommunityUsedSurfaceService;
import app.bpartners.geojobs.service.DetectionAreaComputer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommunityZoneSurfaceAuthorizerTest {
  CommunityUsedSurfaceService communityUsedSurfaceService = mock();
  DetectionAreaComputer detectionAreaComputerMock = mock();
  CommunityZoneSurfaceAuthorizer subject =
      new CommunityZoneSurfaceAuthorizer(communityUsedSurfaceService, detectionAreaComputerMock);

  @Test
  void should_throws_if_max_surface_is_exceeded() {
    when(detectionAreaComputerMock.apply(any(List.class))).thenReturn((2_500.0));
    when(communityUsedSurfaceService.getTotalUsedSurfaceByCommunityId(any(), any()))
        .thenReturn(Optional.of(communityUsedSurface()));
    var communityAuthorization = communityAuthorization();
    List<Feature> features = List.of();
    var error =
        assertThrows(
            ForbiddenException.class,
            () -> {
              subject.accept(communityAuthorization, features);
            });
    assertTrue(error.getMessage().contains("Max surface is exceeded"));
    assertTrue(error.getMessage().contains("3000"));
  }

  @Test
  void should_accept_if_max_surface_is_not_exceeded_yet() {
    when(detectionAreaComputerMock.apply(any(List.class))).thenReturn(1_500.0);
    when(communityUsedSurfaceService.getTotalUsedSurfaceByCommunityId(any(), any()))
        .thenReturn(Optional.empty());
    List<Feature> features = List.of();
    var communityAuthorization = communityAuthorization();

    assertDoesNotThrow(
        () -> {
          subject.accept(communityAuthorization, features);
        });
  }

  private CommunityAuthorization communityAuthorization() {
    return CommunityAuthorization.builder().maxSurface(3_000).build();
  }

  private CommunityUsedSurface communityUsedSurface() {
    return CommunityUsedSurface.builder().usedSurface(1_000).unit(SQUARE_METER).build();
  }
}
