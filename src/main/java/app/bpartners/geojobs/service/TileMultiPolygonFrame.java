package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;

import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TileMultiPolygonFrame
    implements BiFunction<BigDecimal, BigDecimal, Optional<MultiPolygon>> {
  private final TileFinder tileFinder;
  private final GeometryConverter geometryConverter;

  @Override
  public Optional<MultiPolygon> apply(BigDecimal longitude, BigDecimal latitude) {
    var tileCoordinates =
        tileFinder.getSurroundingTiles(longitude, latitude, HOUSES_0.getZoomLevel());
    return tileCoordinates.stream()
        .map(
            tileCoordinate ->
                geometryConverter.getMultiPolygonFromTile(
                    tileCoordinate.getX(), tileCoordinate.getY(), tileCoordinate.getZ()))
        .reduce(
            (multiPolygon1, multiPolygon2) -> {
              var unifiedGeometry = multiPolygon1.union(multiPolygon2);
              if (unifiedGeometry instanceof MultiPolygon multiPolygon) {
                return multiPolygon;
              } else if (unifiedGeometry instanceof org.locationtech.jts.geom.Polygon polygon) {
                return geometryFactory.createMultiPolygon(
                    new org.locationtech.jts.geom.Polygon[] {polygon});
              }
              throw new UnsupportedOperationException(
                  "Unsupported unified geometry : " + unifiedGeometry);
            });
  }
}
