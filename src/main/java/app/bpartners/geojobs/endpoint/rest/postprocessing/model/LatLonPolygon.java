package app.bpartners.geojobs.endpoint.rest.postprocessing.model;

import static app.bpartners.geojobs.endpoint.rest.model.MultiPolygon.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.routeTypeFrom;
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.tan;
import static java.lang.Math.toRadians;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.service.geojson.GeoJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geometry.Position2D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

// unprojected: in degree, such as CRS_CODE = "EPSG:4326"
@Slf4j
public record LatLonPolygon(Polygon polygon) {
  private static final String DEFAULT_LABEL = "line";

  public TiledPolygon tiledPolygon(TilingConf tilingConf) {
    var userData = (Map<String, Object>) polygon.getUserData();
    var label = userData == null ? DEFAULT_LABEL : userData.get("label").toString();
    var originXY = uniqueOrigin(tilingConf);
    return new TiledPolygon(
        toPixelPolygon(polygon, tilingConf, originXY), routeTypeFrom(label), originXY, tilingConf);
  }

  public TiledPolygon tiledPolygon(TilingConf tilingConf, IntXY origin) {
    var userData = (Map<String, Object>) polygon.getUserData();
    var label = userData == null ? DEFAULT_LABEL : userData.get("label").toString();
    return new TiledPolygon(
        toPixelPolygon(polygon, tilingConf, origin), routeTypeFrom(label), origin, tilingConf);
  }

  private IntXY uniqueOrigin(TilingConf tilingConf) {
    var origins =
        Arrays.stream(polygon.getCoordinates())
            .map(m -> originTile(m, tilingConf.z()))
            .map(tileXY -> new Position2D(tileXY.x(), tileXY.y()))
            .collect(groupingBy(identity(), collectingAndThen(counting(), Long::intValue)));
    if (origins.size() != 1) {
      log.warn("origins.size=1 expected: origins={}, p={}", origins, polygon);
    }
    var origin =
        origins.entrySet().stream()
            .max(comparingInt(Map.Entry::getValue))
            .orElseThrow(() -> new IllegalStateException("No origin found"))
            .getKey();

    return new IntXY((int) origin.x, (int) origin.y);
  }

  // Mostly ChatGPT-generated
  public static IntXY originTile(Coordinate mercatorCoordinate, int z) {
    double n = pow(2, z);
    int tileX = (int) floor((mercatorCoordinate.y + 180) / 360 * n);
    double mercXInRad = toRadians(mercatorCoordinate.x);
    double tileYToFloor = (1 - log(tan(mercXInRad) + 1 / cos(mercXInRad)) / PI) / 2 * n;
    int tileY = (int) floor(tileYToFloor);
    return new IntXY(tileX, tileY);
  }

  private Polygon toPixelPolygon(Polygon p, TilingConf tilingConf, IntXY originTile) {
    var latLonCoordinates = p.getExteriorRing().getCoordinates();
    var pixelCoordinates =
        Arrays.stream(latLonCoordinates)
            .map(c -> toPixel(new LatLon(c.x, c.y), tilingConf, originTile))
            .map(pixel -> new Coordinate(pixel.x(), pixel.y()))
            .toArray(Coordinate[]::new);
    var polygon = geometryFactory.createPolygon(pixelCoordinates);
    polygon.setUserData(p.getUserData());
    return polygon;
  }

  public static LatLonPolygon latLon(GeoJson.GeoFeature geoFeature) {
    var geometry = toDomainPolygon(geoFeature.getGeometry());
    geometry.setUserData(geoFeature.getProperties());
    return new LatLonPolygon(geometry);
  }

  public static Polygon toDomainPolygon(
      app.bpartners.geojobs.endpoint.rest.model.MultiPolygon multiPolygon) {
    var coords = multiPolygon.getCoordinates();

    if (coords.size() != 1) {
      throw new IllegalArgumentException("Expected a single polygon, but got " + coords.size());
    }

    List<List<List<BigDecimal>>> polygonCoords = coords.getFirst();
    LinearRing shell = null;
    List<LinearRing> holes = new ArrayList<>();

    for (int i = 0; i < polygonCoords.size(); i++) {
      List<List<BigDecimal>> ring = polygonCoords.get(i);

      Coordinate[] coordinates =
          ring.stream()
              .filter(point -> !point.isEmpty())
              .map(
                  point ->
                      new Coordinate(point.getFirst().doubleValue(), point.getLast().doubleValue()))
              .toArray(Coordinate[]::new);
      if (coordinates.length == 0) {
        continue;
      }
      if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
        Coordinate[] closed = new Coordinate[coordinates.length + 1];
        System.arraycopy(coordinates, 0, closed, 0, coordinates.length);
        closed[closed.length - 1] = closed[0];
        coordinates = closed;
      }

      LinearRing linearRing = geometryFactory.createLinearRing(coordinates);
      if (i == 0) {
        shell = linearRing;
      } else {
        holes.add(linearRing);
      }
    }

    if (shell == null) {
      throw new IllegalStateException("No shell ring found");
    }

    return geometryFactory.createPolygon(shell, holes.toArray(new LinearRing[0]));
  }

  public GeoJson.GeoFeature toGeoFeature() {
    var coordinates = polygon.getCoordinates();
    var properties = (Map<String, Object>) polygon.getUserData();
    List<List<List<List<BigDecimal>>>> multiPolygonCoordinates = new ArrayList<>();

    var ring =
        Arrays.stream(coordinates)
            .map(
                coord ->
                    List.of(BigDecimal.valueOf(coord.getX()), BigDecimal.valueOf(coord.getY())))
            .toList();

    List<List<List<BigDecimal>>> polygonCoords = new ArrayList<>();
    polygonCoords.add(ring);
    multiPolygonCoordinates.add(polygonCoords);
    app.bpartners.geojobs.endpoint.rest.model.MultiPolygon multiPolygon =
        new MultiPolygon().coordinates(multiPolygonCoordinates);
    multiPolygon.setType(MULTI_POLYGON);
    return new GeoJson.GeoFeature(properties, multiPolygon);
  }

  // Mostly ChatGPT-generated
  public static IntXY toPixel(LatLon latLon, TilingConf tilingConf) {
    return toPixel(
        latLon, tilingConf, originTile(new Coordinate(latLon.lat(), latLon.lon()), tilingConf.z()));
  }

  public static IntXY toPixel(LatLon latLon, TilingConf tilingConf, IntXY originTile) {
    int tileSize = 256; // Default tile size
    int scale = tilingConf.imgSize() / tileSize; // Scale factor (4x)
    int z = tilingConf.z();
    double n = pow(2, z);

    // Compute pixel within tile
    var lat = latLon.lat();
    var lon = latLon.lon();
    double x = ((lon + 180) / 360 * n - originTile.x()) * tileSize;
    double y =
        ((1 - log(tan(toRadians(lat)) + 1 / cos(toRadians(lat))) / PI) / 2 * n - originTile.y())
            * tileSize;

    // Convert to 1024x1024 image pixels
    int imgX = (int) round(x * scale);
    int imgY = (int) round(y * scale);

    return new IntXY(imgX, imgY);
  }
}
