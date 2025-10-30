package app.bpartners.geojobs.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.model.TileInfoSize;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.Parcel;
import app.bpartners.geojobs.repository.model.ParcelContent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectionImageTileInfoOriginRetrieverTest {
  private static final TilingTaskRepository tilingTaskRepositoryMock = mock();

  private static final DetectionImageTileInfoOriginRetriever subject =
      new DetectionImageTileInfoOriginRetriever(tilingTaskRepositoryMock);

  @Test
  void returns_null_when_detection_has_no_image_and_zone() {
    var detection = new Detection();

    var actual = subject.apply(detection);

    assertNull(actual);
  }

  @Test
  void returns_null_when_detection_has_no_tiling_job_id() {
    var detection = Detection.builder().imageFileKey("imageKey").polygonGeoJsonZone(mock()).build();

    var actual = subject.apply(detection);
    assertNull(actual);
  }

  @Test
  void returns_null_when_no_tiles_found() {
    var detection =
        Detection.builder()
            .ztjId(randomUUID().toString())
            .imageFileKey("imageKey")
            .polygonGeoJsonZone(mock())
            .build();

    when(tilingTaskRepositoryMock.findAllByJobId(detection.getZtjId())).thenReturn(List.of());

    var actual = subject.apply(detection);
    assertNull(actual);
  }

  @Test
  void returns_top_left_tile_info_with_defaults_tile_info() {
    var detection =
        Detection.builder()
            .ztjId(randomUUID().toString())
            .imageFileKey("imageKey")
            .polygonGeoJsonZone(mock())
            .build();

    var tile1 = toTile(5, 10, null, null);
    var tile2 = toTile(6, 11, null, null);
    var tile3 = toTile(8, 9, null, null);

    when(tilingTaskRepositoryMock.findAllByJobId(detection.getZtjId()))
        .thenReturn(List.of(toTilingTask(List.of(tile1, tile2, tile3))));

    var actual = subject.apply(detection);

    assertEquals(5, actual.getCoordinates().getX());
    assertEquals(9, actual.getCoordinates().getY());
    assertEquals(20, actual.getCoordinates().getZ());
    assertEquals(1024, actual.getSize().getWidth());
    assertEquals(1024, actual.getSize().getHeight());
  }

  @Test
  void returns_top_left_tile_info_with_custom_tile_info() {
    var detection =
        Detection.builder()
            .ztjId(randomUUID().toString())
            .imageFileKey("imageKey")
            .polygonGeoJsonZone(mock())
            .build();

    var size = new TileInfoSize().width(512).height(512);
    var tile1 = toTile(4, 8, 19, size);
    var tile2 = toTile(5, 9, 19, size);

    when(tilingTaskRepositoryMock.findAllByJobId(detection.getZtjId()))
        .thenReturn(List.of(toTilingTask(List.of(tile1, tile2))));

    var actual = subject.apply(detection);

    assertEquals(4, actual.getCoordinates().getX());
    assertEquals(8, actual.getCoordinates().getY());
    assertEquals(19, actual.getCoordinates().getZ());
    assertEquals(size.getWidth(), actual.getSize().getWidth());
    assertEquals(size.getHeight(), actual.getSize().getHeight());
  }

  private static Tile toTile(int x, int y, Integer z, TileInfoSize size) {
    var coordinates = new TileCoordinates().x(x).y(y).z(z);
    return Tile.builder().coordinates(coordinates).size(size).build();
  }

  private static ParcelTilingTask toTilingTask(List<Tile> tiles) {
    var content = ParcelContent.builder().tiles(tiles).build();
    var parcel = Parcel.builder().parcelContent(content).build();
    return ParcelTilingTask.builder().parcels(List.of(parcel)).build();
  }
}
