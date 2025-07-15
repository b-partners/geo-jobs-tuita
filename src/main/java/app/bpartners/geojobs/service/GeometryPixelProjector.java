package app.bpartners.geojobs.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

@Component
public class GeometryPixelProjector {

  public List<List<List<List<BigDecimal>>>> toMultiPolygonPixels(
      Geometry geometry, int tileX, int tileY, int zoom, int tileSizePx) {
    List<List<List<List<BigDecimal>>>> result = new ArrayList<>();
    if (geometry == null || geometry.isEmpty()) {
      return result;
    }

    switch (geometry) {
      case Polygon polygon -> {
        result.add(projectMultiPolygon(polygon, tileX, tileY, zoom, tileSizePx));
      }
      case MultiPolygon multiPolygon -> {
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
          Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
          result.add(projectMultiPolygon(polygon, tileX, tileY, zoom, tileSizePx));
        }
      }
      case Point ignored -> {
        // rien à faire
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported geometry type: " + geometry.getGeometryType());
    }
    return result;
  }

  public List<List<BigDecimal>> toPixels(
      Geometry geometry, int tileX, int tileY, int zoom, int tileSizePx) {
    List<List<BigDecimal>> result = new ArrayList<>();
    if (geometry == null || geometry.isEmpty()) {
      return result;
    }

    switch (geometry) {
      case Polygon polygon ->
          result.addAll(projectPolygon(polygon, tileX, tileY, zoom, tileSizePx));
      case MultiPolygon multiPolygon -> {
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
          Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
          result.addAll(projectPolygon(polygon, tileX, tileY, zoom, tileSizePx));
        }
      }
      case Point ignored -> {}
      default ->
          throw new IllegalArgumentException(
              "Unsupported geometry type: " + geometry.getGeometryType());
    }
    return result;
  }

  private List<List<BigDecimal>> projectPolygon(
      Polygon polygon, int tileX, int tileY, int zoom, int tileSizePx) {
    List<List<BigDecimal>> pixelRings = new ArrayList<>();

    // Extérieur
    pixelRings.addAll(projectRing(polygon.getExteriorRing(), tileX, tileY, zoom, tileSizePx));

    // Intérieurs (trous)
    for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
      pixelRings.addAll(projectRing(polygon.getInteriorRingN(j), tileX, tileY, zoom, tileSizePx));
    }

    return pixelRings;
  }

  private List<List<List<BigDecimal>>> projectMultiPolygon(
      Polygon polygon, int tileX, int tileY, int zoom, int tileSizePx) {
    List<List<List<BigDecimal>>> pixelRings = new ArrayList<>();

    // Extérieur
    pixelRings.add(projectRing(polygon.getExteriorRing(), tileX, tileY, zoom, tileSizePx));

    // Intérieurs (trous)
    for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
      pixelRings.add(projectRing(polygon.getInteriorRingN(j), tileX, tileY, zoom, tileSizePx));
    }

    return pixelRings;
  }

  private List<List<BigDecimal>> projectRing(
      LineString ring, int tileX, int tileY, int zoom, int tileSizePx) {
    List<List<BigDecimal>> pixelCoords = new ArrayList<>();
    Coordinate[] coords = ring.getCoordinates();

    for (Coordinate coord : coords) {
      BigDecimal[] px = lonLatToPixelInTile(coord.x, coord.y, tileX, tileY, zoom, tileSizePx);
      List<BigDecimal> point = new ArrayList<>();
      point.add(px[0]); // x
      point.add(px[1]); // y
      pixelCoords.add(point);
    }

    return pixelCoords;
  }

  private BigDecimal[] lonLatToPixelInTile(
      double lon, double lat, int tileX, int tileY, int zoom, int tileSizePx) {
    double n = Math.pow(2.0, zoom);

    double x = (lon + 180.0) / 360.0 * n;
    double latRad = Math.toRadians(lat);
    double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

    double pixelX = (x - tileX) * tileSizePx;
    double pixelY = (y - tileY) * tileSizePx;

    return new BigDecimal[] {BigDecimal.valueOf(pixelX), BigDecimal.valueOf(pixelY)};
  }
}
