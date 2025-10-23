package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum.ROOF;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.awt.Color.WHITE;

import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TileImageBlur implements BiFunction<Detection, List<Tile>, List<Tile>> {
  private static final int DEFAULT_TILE_SIZE = 1024;
  private final GeometryPixelProjector geometryPixelProjector;
  private final GeometryConverter geometryConverter;
  private final FilePolygonDrawer filePolygonDrawer;
  private final DetectionBackgroundRetriever detectionBackgroundRetriever;
  private final DetectionProvidedZoneUnifier detectionProvidedZoneUnifier;

  @Override
  public List<Tile> apply(Detection detection, List<Tile> tiles) {
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
    return tiles.stream()
        .map(
            tile -> {
              var tileCoordinates = tile.getCoordinates();
              var multiPolygonFromTile =
                  geometryConverter.getMultiPolygonFromTile(
                      tileCoordinates.getX(), tileCoordinates.getY(), tileCoordinates.getZ());
              var roofInsideTileAndProvidedZone =
                  multiPolygonFromTile.intersection(roofInsideProvidedZone);

              Geometry intersectionBetweenTileMultiPolygonAndBackground;
              if (ROOF.equals(detection.getGeoJsonDelimitationType())) {
                intersectionBetweenTileMultiPolygonAndBackground =
                    multiPolygonFromTile.difference(roofInsideTileAndProvidedZone);
              } else {
                intersectionBetweenTileMultiPolygonAndBackground =
                    multiPolygonFromTile.intersection(latLonBackgroundInsideProvidedZone);
              }

              var tileWithoutRoofInsideTileAndZone =
                  multiPolygonFromTile.difference(roofInsideTileAndProvidedZone);
              List<List<List<IntXY>>> multiPolygonPixelCoordinates;
              if (intersectionBetweenTileMultiPolygonAndBackground.isEmpty()) {
                multiPolygonPixelCoordinates = getBlurAllAreaCoordinates();
              } else {
                var backgroundMultiPolygonPixels =
                    geometryPixelProjector.toMultiPolygonPixels(
                        tileWithoutRoofInsideTileAndZone,
                        tileCoordinates.getX(),
                        tileCoordinates.getY(),
                        tileCoordinates.getZ(),
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
              var imageWithBlur =
                  filePolygonDrawer.apply(multiPolygonPixelCoordinates, WHITE, tile.getImage());
              return tile.toBuilder().image(imageWithBlur).build();
            })
        .toList();
  }

  private List<List<List<IntXY>>> getBlurAllAreaCoordinates() {
    return List.of(
        List.of(
            List.of(
                new IntXY(0, 0),
                new IntXY(0, DEFAULT_TILE_SIZE),
                new IntXY(DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE),
                new IntXY(DEFAULT_TILE_SIZE, 0))));
  }
}
