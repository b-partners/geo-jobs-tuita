package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.awt.Color.WHITE;

import app.bpartners.geojobs.endpoint.event.model.TileExtendedImageRequested;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.service.DetectionBackgroundRetriever;
import app.bpartners.geojobs.service.DetectionProvidedZoneUnifier;
import app.bpartners.geojobs.service.FilePolygonDrawer;
import app.bpartners.geojobs.service.GeometryPixelProjector;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tile19.ExtenderApi;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TileExtendedImageRequestedService implements Consumer<TileExtendedImageRequested> {
  private static final int DEFAULT_TILE_SIZE = 1024;
  private final TileFinder finder;
  private final BucketComponent bucketComponent;
  private final ExtenderApi extenderApi;
  private final FileWriter fileWriter;
  private final GeometryPixelProjector geometryPixelProjector;
  private final GeometryConverter geometryConverter;
  private final FilePolygonDrawer filePolygonDrawer;
  private final DetectionBackgroundRetriever detectionBackgroundRetriever;
  private final DetectionProvidedZoneUnifier detectionProvidedZoneUnifier;

  @Override
  public void accept(TileExtendedImageRequested event) {
    var layer = event.getLayer();
    var longitude = event.getLongitude();
    var latitude = event.getLatitude();
    var detection = event.getDetection();
    var latLonBackgroundInsideProvidedZone = detectionBackgroundRetriever.apply(detection);
    var providedZone = detectionProvidedZoneUnifier.apply(detection);
    var unifiedRoofMultiPolygon =
        detection.getFeatureWithDelimitations().stream()
            .map(
                featureWithDelimitation ->
                    featureWithDelimitation.delimitations().stream()
                        .map(
                            f -> {
                              var geometryType = toRestFeature(f).getGeometry().getActualInstance();
                              switch (geometryType) {
                                case Polygon polygon -> {
                                  return geometryConverter.apply(List.of(polygon.getCoordinates()));
                                }
                                case MultiPolygon multiPolygon -> {
                                  return geometryConverter.apply(multiPolygon.getCoordinates());
                                }
                                default ->
                                    throw new IllegalArgumentException(
                                        "Unsupported geometry type to extended image: "
                                            + geometryType);
                              }
                            })
                        .toList())
            .toList()
            .stream()
            .flatMap(List::stream)
            .reduce(unifyMultiPolygon())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to unify delimitation multiPolygon for detection.id: "
                            + detection.getId()));
    var roofInsideProvidedZone = providedZone.intersection(unifiedRoofMultiPolygon);
    var tileCoordinates = finder.getSurroundingTiles(longitude, latitude, event.getZoom());
    var tileImagesFiles =
        tileCoordinates.stream()
            .map(
                coor -> {
                  var multiPolygonFromTile =
                      geometryConverter.getMultiPolygonFromTile(
                          coor.getX(), coor.getY(), coor.getZ());
                  var roofInsideTileAndProvidedZone =
                      multiPolygonFromTile.intersection(roofInsideProvidedZone);
                  var intersectionBetweenTileMultiPolygonAndBackground =
                      multiPolygonFromTile.intersection(latLonBackgroundInsideProvidedZone);
                  var tileWithoutRoofInsideTileAndZone =
                      multiPolygonFromTile.difference(roofInsideTileAndProvidedZone);
                  List<List<List<IntXY>>> multiPolygonPixelCoordinates;
                  var fileKey =
                      layer + "/" + coor.getZ() + "/" + coor.getX() + "/" + coor.getY() + ".jpg";
                  if (intersectionBetweenTileMultiPolygonAndBackground.isEmpty()) {
                    multiPolygonPixelCoordinates = getBlurAllAreaCoordinates();
                  } else {
                    var backgroundMultiPolygonPixels =
                        geometryPixelProjector.toMultiPolygonPixels(
                            tileWithoutRoofInsideTileAndZone,
                            coor.getX(),
                            coor.getY(),
                            coor.getZ(),
                            DEFAULT_TILE_SIZE);
                    multiPolygonPixelCoordinates =
                        backgroundMultiPolygonPixels.stream()
                            .map(
                                polygon ->
                                    polygon.stream()
                                        .map(
                                            ring ->
                                                ring.stream()
                                                    .map(
                                                        coordinates ->
                                                            new IntXY(
                                                                coordinates.getFirst().intValue(),
                                                                coordinates.getLast().intValue()))
                                                    .toList())
                                        .toList())
                            .toList();
                  }
                  var originalImage = bucketComponent.download(fileKey);
                  return filePolygonDrawer.apply(
                      multiPolygonPixelCoordinates, WHITE, originalImage);
                })
            .toList();

    var extendedImageBase64 = extenderApi.apply(tileImagesFiles);

    var filename = "extended_original_" + longitude + "_" + latitude;
    var extendedImageFile = fileWriter.base64ToFile(extendedImageBase64, filename);
    var extendedImageKey = layer + "/" + filename + ".jpg";
    bucketComponent.upload(extendedImageFile, extendedImageKey);

    extendedImageFile.delete();
  }

  private static List<List<List<IntXY>>> getBlurAllAreaCoordinates() {
    return List.of(
        List.of(
            List.of(
                new IntXY(0, 0),
                new IntXY(0, DEFAULT_TILE_SIZE),
                new IntXY(DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE),
                new IntXY(DEFAULT_TILE_SIZE, 0))));
  }
}
