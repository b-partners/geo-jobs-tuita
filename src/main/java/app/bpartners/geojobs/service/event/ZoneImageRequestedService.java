package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.model.ZoneImageRequested;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.TileImageBlur;
import app.bpartners.geojobs.service.TileImagesAssembler;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneImageRequestedService implements Consumer<ZoneImageRequested> {
  private static final int ONE_KILOMETRE_SQUARE_AREA = 1_000_000;
  private final DetectionRepository detectionRepository;
  private final GeometryConverter geometryConverter;
  private final BucketComponent bucketComponent;
  private final TileImagesAssembler tileImagesAssembler;
  private final TilingTaskRepository tilingTaskRepository;
  private final GeometrySquareMeterArea geometrySquareMeterArea;
  private final TileImageBlur tileImageBlur;

  @SneakyThrows
  @Override
  public void accept(ZoneImageRequested event) {
    var detectionIdentifier = event.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    var polygonGeometry =
        geometryConverter.convertToPolygon(
            detection
                .getPolygonGeoJsonZone()
                .getGeometry()
                .getPolygon()
                .getCoordinates()
                .getFirst());

    var actualArea = geometrySquareMeterArea.apply(polygonGeometry);
    if (actualArea > ONE_KILOMETRE_SQUARE_AREA) {
      log.warn(
          "Zone image requested not implemented for zone over 1km^2, otherwise actual provided"
              + " polygon is "
              + actualArea
              + " for detection.id="
              + detectionIdentifier);
      return;
    }
    // TODO : paginate consumption to handle more than 1km^2
    var tilingJobIdentifier = detection.getZtjId();
    var tilingTasks = tilingTaskRepository.findAllByJobId(tilingJobIdentifier);
    var tiles = tilingTasks.stream().map(ParcelTilingTask::getTiles).flatMap(List::stream).toList();
    var tilesWithImages =
        tiles.stream()
            .map(
                tile ->
                    tile.toBuilder().image(bucketComponent.download(tile.getBucketPath())).build())
            .toList();
    var tilesWithBlur = tileImageBlur.apply(detection, tilesWithImages);

    var assembleImageFile = tileImagesAssembler.apply(tilesWithBlur);

    bucketComponent.upload(assembleImageFile, "zone_images/" + detection.getId() + ".jpg");
  }
}
