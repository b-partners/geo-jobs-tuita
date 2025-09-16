package app.bpartners.geojobs.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.utils.ImageComparator;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TileImagesAssemblerTest {
  ImageComparator imageComparator = new ImageComparator();
  TileImagesAssembler subject = new TileImagesAssembler();

  @SneakyThrows
  @Test
  void tiles_assemble_to_one_image() {
    var zoom = 20;

    var actual =
        subject.apply(
            List.of(
                Tile.builder()
                    .image(new ClassPathResource("/images/extender/61-92.jpg").getFile())
                    .coordinates(new TileCoordinates().x(523561).y(370292).z(zoom))
                    .build(),
                Tile.builder()
                    .image(new ClassPathResource("/images/extender/62-92.jpg").getFile())
                    .coordinates(new TileCoordinates().x(523562).y(370292).z(zoom))
                    .build(),
                Tile.builder()
                    .image(new ClassPathResource("/images/extender/63-92.jpg").getFile())
                    .coordinates(new TileCoordinates().x(523563).y(370292).z(zoom))
                    .build(),
                Tile.builder()
                    .image(new ClassPathResource("/images/extender/61-93.jpg").getFile())
                    .coordinates(new TileCoordinates().x(523561).y(370293).z(zoom))
                    .build()));

    assertNotNull(actual);
    assertTrue(
        imageComparator.apply(
            actual, new ClassPathResource("/images/assemble_image.jpg").getFile()));
  }
}
