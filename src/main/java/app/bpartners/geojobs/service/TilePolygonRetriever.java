package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;

import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TilePolygonRetriever implements Function<Polygon, List<Polygon>> {
  private final TileFinder tileFinder;
  private final GeometryConverter geometryConverter;

  @Override
  public List<Polygon> apply(Polygon geometryPolygon) {
    var tileCoordinatesFromPolygon =
        tileFinder.getFromGeoJsonPolygon(geometryPolygon, HOUSES_0.getZoomLevel());
    return tileCoordinatesFromPolygon.stream()
        .map(
            coordinates ->
                geometryConverter.getMultiPolygonFromTile(
                    coordinates.getX(), coordinates.getY(), coordinates.getZ()))
        .map(multiPolygon -> (org.locationtech.jts.geom.Polygon) multiPolygon.getGeometryN(0))
        .toList();
  }
}
