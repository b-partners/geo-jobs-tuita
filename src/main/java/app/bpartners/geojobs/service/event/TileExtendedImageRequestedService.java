package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;

import app.bpartners.geojobs.endpoint.event.model.TileExtendedImageRequested;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.*;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TileExtendedImageRequestedService implements Consumer<TileExtendedImageRequested> {
  private final TileFinder finder;
  private final BucketComponent bucketComponent;
  private final DetectionRepository detectionRepository;
  private final TileImageBlur tileImageBlur;
  private final TileImagesAssembler tileImagesAssembler;

  @Override
  public void accept(TileExtendedImageRequested event) {
    var longitude = event.getLongitude();
    var latitude = event.getLatitude();
    var detectionIdentifier = event.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    var layer = detection.getGeoServerProperties().getGeoServerParameter().getLayers();
    var surroundingTileCoordinates =
        finder.getSurroundingTiles(longitude, latitude, HOUSES_0.getZoomLevel());
    var tileWithOriginalImages =
        surroundingTileCoordinates.stream()
            .map(
                tileCoordinates -> {
                  var fileKey =
                      layer
                          + "/"
                          + tileCoordinates.getZ()
                          + "/"
                          + tileCoordinates.getX()
                          + "/"
                          + tileCoordinates.getY()
                          + ".jpg";
                  return Tile.builder()
                      .coordinates(tileCoordinates)
                      .image(bucketComponent.download(fileKey))
                      .build();
                })
            .toList();
    var tileImagesFiles = tileImageBlur.apply(detection, tileWithOriginalImages);

    var extendedImageFile = tileImagesAssembler.apply(tileImagesFiles);

    var filename = "extended_original_" + longitude + "_" + latitude;
    var extendedImageKey = layer + "/" + filename + ".jpg";
    bucketComponent.upload(extendedImageFile, extendedImageKey);
  }
}
