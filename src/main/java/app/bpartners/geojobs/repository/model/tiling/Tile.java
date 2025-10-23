package app.bpartners.geojobs.repository.model.tiling;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.model.TileInfoSize;
import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Tile implements Serializable {
  private String id;
  private Instant creationDatetime;
  private TileInfoSize size;
  private TileCoordinates coordinates;
  private String bucketPath;
  private File image;
  private String detectionE2Id;

  @Override
  public String toString() {
    return "Tile{"
        + "id='"
        + id
        + '\''
        + ", creationDatetime="
        + creationDatetime
        + ", size="
        + size
        + ", coordinates="
        + coordinates
        + ", bucketPath='"
        + bucketPath
        + '\''
        + '}';
  }

  public Tile duplicate(String tileId) {
    return Tile.builder()
        .id(tileId)
        .creationDatetime(this.creationDatetime)
        .size(this.size)
        .coordinates(this.coordinates)
        .bucketPath(this.bucketPath)
        .build();
  }
}
