package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;
import static javax.imageio.ImageIO.read;

import app.bpartners.geojobs.endpoint.event.model.FeatureImageRequested;
import app.bpartners.geojobs.file.WhiteImageDetector;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.TileImageBlur;
import app.bpartners.geojobs.service.TileImagesAssembler;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureImageRequestedService implements Consumer<FeatureImageRequested> {
  private static final int ONE_KILOMETRE_SQUARE_AREA = 1_000_000;
  private final DetectionRepository detectionRepository;
  private final GeometryConverter geometryConverter;
  private final BucketComponent bucketComponent;
  private final TileImagesAssembler tileImagesAssembler;
  private final TilingTaskRepository tilingTaskRepository;
  private final GeometrySquareMeterArea geometrySquareMeterArea;
  private final TileImageBlur tileImageBlur;
  private final WhiteImageDetector whiteImageDetector;
  private final TileFinder tileFinder;

  @SneakyThrows
  @Override
  public void accept(FeatureImageRequested event) {
    var feature = event.getFeature();
    var polygonGeometry = geometryConverter.retrievePolygonGeometry(feature);
    if (polygonGeometry == null) return;
    var detectionIdentifier = event.getDetectionIdentifier();
    var actualArea = geometrySquareMeterArea.apply(polygonGeometry);
    if (actualArea > ONE_KILOMETRE_SQUARE_AREA) {
      log.warn(
          "Feature image requested not implemented for zone over 1km^2, otherwise actual provided"
              + " polygon is {} for detection.id={} and feature {}",
          actualArea,
          detectionIdentifier,
          feature);
      return;
    }
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    // TODO : paginate finding tilingTasks to optimize performance
    var tilingJobIdentifier = detection.getZtjId();
    var tilingTasks = tilingTaskRepository.findAllByJobId(tilingJobIdentifier);
    var tileCoordinatesEnvelopingPolygon =
        tileFinder.getFromGeoJsonPolygon(polygonGeometry, HOUSES_0.getZoomLevel());
    var tiles =
        tilingTasks.stream()
            .map(ParcelTilingTask::getTiles)
            .flatMap(List::stream)
            .filter(tile -> tileCoordinatesEnvelopingPolygon.contains(tile.getCoordinates()))
            .toList();
    var tilesWithImages =
        tiles.stream()
            .map(
                tile ->
                    tile.toBuilder().image(bucketComponent.download(tile.getBucketPath())).build())
            .toList();
    var tilesWithBlur = tileImageBlur.apply(detection, tilesWithImages);

    var assembleImageFile = tileImagesAssembler.apply(tilesWithBlur);

    if (whiteImageDetector.apply(read(assembleImageFile))) {
      throw new NotImplementedException(
          "Address " + detection.getZoneName() + " not supported for now");
    }

    if (event.getFeatureNb() == 0) {
      bucketComponent.upload(assembleImageFile, "zone_images/" + detection.getId() + ".jpg");
    }
    bucketComponent.upload(
        assembleImageFile,
        "zone_images/"
            + detection.getId()
            + "/"
            + event.getFeatureNb()
            + "/"
            + detection.getZoneName()
            + ".jpg");
  }
}
