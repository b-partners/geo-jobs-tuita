package app.bpartners.geojobs.service;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TileCoordinatesPolygonIntersection {
  private static final int DEFAULT_PIXEL_SIZE = 1024;
  private final GeometryPixelProjector geometryPixelProjector;
  private final GeometryConverter geometryConverter;

  public List<List<BigDecimal>> intersects(Geometry latLonRoofMultiPolygon, int x, int y, int z) {
    var geometryIntersectionWithTile =
        intersection(latLonRoofMultiPolygon, new TileCoordinates().x(x).y(y).z(z));
    return geometryPixelProjector.toPixels(
        geometryIntersectionWithTile, x, y, z, DEFAULT_PIXEL_SIZE);
  }

  public List<List<BigDecimal>> intersects(
      Geometry latLonRoofMultiPolygon, TileCoordinates tileCoordinates) {
    return intersects(
        latLonRoofMultiPolygon,
        tileCoordinates.getX(),
        tileCoordinates.getY(),
        tileCoordinates.getZ());
  }

  public Geometry intersection(Geometry multiPolygon, TileCoordinates tileCoordinates) {
    var xTile = tileCoordinates.getX();
    var yTile = tileCoordinates.getY();
    var zTile = tileCoordinates.getZ();
    var multiPolygonFromTile = geometryConverter.getMultiPolygonFromTile(xTile, yTile, zTile);
    return multiPolygon.intersection(multiPolygonFromTile);
  }
}
