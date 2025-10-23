package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.repository.model.detection.RoofCoveringType.ROOF_ASPHALTE_BITUME;
import static app.bpartners.geojobs.repository.model.detection.RoofCoveringType.ROOF_TUILES;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.file.bucket.BucketConf;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.detection.RoofCovering;
import app.bpartners.geojobs.service.detection.RoofCoveringDetector;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;

class RoofCoveringDetectorIT extends FacadeIT {
  private static final String TILE_BUCKET_PATH = "ALPES-MARITIMES_2024_5cm/20/544680/383095.jpg";
  @MockBean CustomBucketComponent customBucketComponentMock;
  @MockBean BucketConf bucketConfMock;
  @Autowired RoofCoveringDetector subject;

  @SneakyThrows
  @Test
  void detect_roof_covering_ok() {
    var tileId = randomUUID().toString();
    var maskFile = new ClassPathResource("/images/mask/mask.png").getFile();
    when(bucketConfMock.getBucketName()).thenReturn("bucketName");
    when(customBucketComponentMock.getBucketConf()).thenReturn(bucketConfMock);
    when(customBucketComponentMock.download("bucketName", TILE_BUCKET_PATH))
        .thenReturn(new ClassPathResource("images/" + TILE_BUCKET_PATH).getFile());

    var actual =
        subject.apply(
            Tile.builder()
                .id(tileId)
                .bucketPath(TILE_BUCKET_PATH)
                .coordinates(new TileCoordinates().x(544680).y(383095).z(20))
                .build(),
            maskFile);

    assertEquals(expectedCovering(), actual);
  }

  private RoofCoveringDetector.@NotNull RoofCoveringDetectionResponse expectedCovering() {
    return new RoofCoveringDetector.RoofCoveringDetectionResponse(
        new RoofCovering(ROOF_TUILES, 80140L), new RoofCovering(ROOF_ASPHALTE_BITUME, 31096L));
  }
}
