package app.bpartners.geojobs.service;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.model.TileInfo;
import app.bpartners.geojobs.endpoint.rest.model.TileInfoSize;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionImageTileInfoOriginRetriever implements Function<Detection, TileInfo> {
  private final TilingTaskRepository tilingTaskRepository;
  private static final int DEFAULT_TILE_ZOOM = 20;
  private static final int DEFAULT_TILE_SIZE = 1024;

  @Override
  public TileInfo apply(Detection detection) {
    if (detection.getImageFileKey() == null && detection.getPolygonGeoJsonZone() == null) {
      return null;
    }

    var tilingJobIdentifier = detection.getZtjId();
    if (tilingJobIdentifier == null) {
      return null;
    }

    var tilingTasks = tilingTaskRepository.findAllByJobId(tilingJobIdentifier);
    var tiles = tilingTasks.stream().map(ParcelTilingTask::getTiles).flatMap(List::stream).toList();

    return getTopLeftTileInfo(tiles);
  }

  private static TileInfo getTopLeftTileInfo(List<Tile> tiles) {
    if (tiles.isEmpty()) {
      return null;
    }

    var first = tiles.getFirst();
    var tileZ =
        first.getCoordinates().getZ() == null ? DEFAULT_TILE_ZOOM : first.getCoordinates().getZ();
    var tileSize =
        tiles.getFirst().getSize() == null ? defaultTileInfoSize() : tiles.getFirst().getSize();

    var tilesCoordinates = tiles.stream().map(Tile::getCoordinates).toList();
    var tileX = tilesCoordinates.stream().mapToInt(TileCoordinates::getX).min().orElse(0);
    var tileY = tilesCoordinates.stream().mapToInt(TileCoordinates::getY).min().orElse(0);

    var coordinate = new TileCoordinates().x(tileX).y(tileY).z(tileZ);

    return new TileInfo().size(tileSize).coordinates(coordinate);
  }

  private static TileInfoSize defaultTileInfoSize() {
    return new TileInfoSize().height(DEFAULT_TILE_SIZE).width(DEFAULT_TILE_SIZE);
  }
}
