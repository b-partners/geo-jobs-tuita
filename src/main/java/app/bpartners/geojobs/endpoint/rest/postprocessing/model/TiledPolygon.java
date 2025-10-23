package app.bpartners.geojobs.endpoint.rest.postprocessing.model;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.routeTypeFrom;
import static java.lang.Math.PI;
import static java.lang.Math.atan;
import static java.lang.Math.pow;
import static java.lang.Math.sinh;
import static java.lang.Math.toDegrees;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.rest.model.DetectedObject;
import app.bpartners.geojobs.endpoint.rest.model.DetectedTile;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.TileCoordinatesFromFileName;
import app.bpartners.geojobs.model.geometry.VGG;
import app.bpartners.geojobs.model.geometry.route.ObjectType;
import java.math.BigDecimal;
import java.util.*;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

@Slf4j
// projected: in meter, such as CRS_CODE = "EPSG:3857"
public record TiledPolygon(
    Polygon polygon, ObjectType type, IntXY originTile, TilingConf tilingConf) {

  public LatLonPolygon latLonPolygon() {
    var exteriorLatLonCoordinates = getExteriorLatLonCoordinates(polygon, null);
    return latLonPolygon(exteriorLatLonCoordinates);
  }

  public LatLonPolygon latLonPolygon(IntXY originTile) {
    var exteriorLatLonCoordinates = getExteriorLatLonCoordinates(polygon, originTile);
    return latLonPolygon(exteriorLatLonCoordinates);
  }

  private LatLonPolygon latLonPolygon(Coordinate[] coords) {
    LinearRing exteriorRing = geometryFactory.createLinearRing(coords);

    var latLonHolesCoordinates = getHolesLatLonCoordinates(polygon);
    LinearRing[] holes = new LinearRing[latLonHolesCoordinates.length];
    for (int i = 0; i < latLonHolesCoordinates.length; i++) {
      holes[i] = geometryFactory.createLinearRing(latLonHolesCoordinates[i]);
    }
    var p = geometryFactory.createPolygon(exteriorRing, holes);
    p.setUserData(polygon.getUserData());
    return new LatLonPolygon(p);
  }

  private Coordinate[] getExteriorLatLonCoordinates(Polygon polygon, @Nullable IntXY originTile) {
    var origin = originTile == null ? this.originTile : originTile;
    return Arrays.stream(polygon.getExteriorRing().getCoordinates())
        .map(c -> toLatLon(origin, tilingConf, new IntXY((int) c.x, (int) c.y)))
        .toArray(Coordinate[]::new);
  }

  private Coordinate[][] getHolesLatLonCoordinates(Polygon polygon) {
    int holesNb = polygon.getNumInteriorRing();
    LinearRing[] holes = new LinearRing[holesNb];
    for (int n = 0; n < holesNb; n++) {
      holes[n] = geometryFactory.createLinearRing(polygon.getInteriorRingN(n).getCoordinates());
    }
    return Arrays.stream(holes)
        .map(
            hole ->
                Arrays.stream(hole.getCoordinates())
                    .map(c -> toLatLon(originTile, tilingConf, new IntXY((int) c.x, (int) c.y)))
                    .toArray(Coordinate[]::new))
        .toArray(Coordinate[][]::new);
  }

  public static Set<TiledPolygon> newTiledPolygons(Set<DetectedTile> tiles, int imgSize) {
    Set<TiledPolygon> res = new HashSet<>();
    for (var t : tiles) {
      for (var o : t.getDetectedObjects()) {
        var tiledPolygon = tiledPolygon(t, o, imgSize);
        res.add(tiledPolygon);
      }
    }
    return res;
  }

  public static Set<TiledPolygon> toTiledPolygons(
      TilingConf tilingConf, VGG vgg, boolean isZXYDotFiletype) {
    var annotations = vgg.values();
    return annotations.stream()
        .map(
            annotation -> {
              var filename = annotation.getFilename();
              var regions = annotation.getRegions();
              return newTiledPolygons(filename, regions, tilingConf, isZXYDotFiletype);
            })
        .flatMap(Collection::stream)
        .collect(toSet());
  }

  public static Set<TiledPolygon> newTiledPolygons(
      String filename,
      Map<String, VGG.Annotation.Region> vggRegions,
      TilingConf tilingConf,
      boolean isZXYDotFiletype) {
    Set<TiledPolygon> res = new HashSet<>();
    var regions = vggRegions.values();
    for (var r : regions) {
      res.add(tiledPolygon(filename, r, tilingConf, isZXYDotFiletype));
    }
    return res;
  }

  private static TiledPolygon tiledPolygon(
      DetectedTile detectedTile, DetectedObject detectedObject, int imgSize) {
    var restPolygon = detectedObject.getFeature().getGeometry().getPolygon();
    var tileInfo = detectedTile.getTileInfo();
    var tileCoordinates = tileInfo.getCoordinates();
    var originTile = new IntXY(tileCoordinates.getX(), tileCoordinates.getZ());
    return new TiledPolygon(
        polygon(restPolygon),
        routeTypeFrom(detectedObject.getDetectedObjectType()),
        originTile,
        new TilingConf(tileCoordinates.getZ(), imgSize));
  }

  private static TiledPolygon tiledPolygon(
      String filename,
      VGG.Annotation.Region vggRegion,
      TilingConf tilingConf,
      boolean isZXYDotFiletype) {
    var shapeAttribute = vggRegion.getShapeAttribute();
    var label = vggRegion.getRegionAttribute().get("label").toString();
    var coordsExtractor = new TileCoordinatesFromFileName(isZXYDotFiletype);
    var originTile = new IntXY(coordsExtractor.x(filename), coordsExtractor.y(filename));
    var polygon = polygon(shapeAttribute);
    polygon.setUserData(Map.of("label", label));
    return new TiledPolygon(polygon, routeTypeFrom(label), originTile, tilingConf);
  }

  private static Polygon polygon(app.bpartners.geojobs.endpoint.rest.model.Polygon restP) {
    List<List<List<BigDecimal>>> restPCoordinates = restP.getCoordinates();
    if (restPCoordinates.size() != 1) {
      throw new IllegalArgumentException("Single Polygon expected but got: " + restP);
    }

    var onlyPolygonCoordinates = restPCoordinates.get(0);
    return geometryFactory.createPolygon(
        onlyPolygonCoordinates.stream()
            .map(c -> new Coordinate(c.get(0).doubleValue(), c.get(1).doubleValue()))
            .toArray(Coordinate[]::new));
  }

  public static Polygon polygon(VGG.Annotation.Region.ShapeAttribute vggShapeAttribute) {
    var allX = vggShapeAttribute.getAllPointsX();
    var allY = vggShapeAttribute.getAllPointsY();
    var polygonLength = allX.size();
    Coordinate[] coordinates = new Coordinate[polygonLength];
    for (int i = 0; i < polygonLength; i++) {
      var pixel = new IntXY(allX.get(i).intValue(), allY.get(i).intValue());
      coordinates[i] = new Coordinate(pixel.x(), pixel.y());
    }
    if (!coordinates[0].equals(coordinates[coordinates.length - 1])) {
      var clone = new Coordinate[coordinates.length + 1];
      System.arraycopy(coordinates, 0, clone, 0, coordinates.length);
      clone[coordinates.length] = coordinates[0];
      coordinates = clone;
    }
    return geometryFactory.createPolygon(coordinates);
  }

  // Mostly ChatGPT-generated
  public static Coordinate toLatLon(IntXY originTile, TilingConf tilingConf, IntXY pixel) {
    int tileSize = tilingConf.imgSize();
    int scale = tilingConf.imgSize() / tileSize; // Scale factor (4x)

    // Convert image pixel to tile pixel
    double tilePX = pixel.x() / (double) scale;
    double tilePY = pixel.y() / (double) scale;

    // Convert back to lat/lon
    double n = pow(2, tilingConf.z());
    double lon = (originTile.x() + tilePX / tileSize) / n * 360.0 - 180.0;
    double lat = toDegrees(atan(sinh(PI * (1 - 2 * (originTile.y() + tilePY / tileSize) / n))));

    return new Coordinate(lat, lon);
  }
}
