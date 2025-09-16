package app.bpartners.geojobs.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.area.AreaRateComputerFacade.*;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.TOITURE_REVETEMENT;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.model.geometry.area.AreaRateComputerFacade;
import app.bpartners.geojobs.model.geometry.area.DominantRoof;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class VGGFactory implements Converter<Set<Polygon>, VGG> {
  private static final int DEFAULT_IMG_SIZE = 1024;
  private final FeatureMapper featureMapper;
  private final TileCoordinatesPolygonIntersection tilePolygonIntersection;
  private final GeometryConverter geometryConverter;
  private final GeometrySquareMeterArea geometrySquareMeterArea;
  private final TileFinder tileFinder;

  @Override
  public VGG convert(Set<Polygon> polygons) {
    var vgg = new VGG();
    for (Polygon p : polygons) {
      var metadata = (HashMap) p.getUserData();
      var key = metadata.get("filename").toString();
      var label = metadata.get("label").toString();
      var confidence = metadata.get("confidence");
      var confidenceAsDouble =
          confidence == null ? null : Double.parseDouble(confidence.toString());
      Map<String, VGG.Annotation.Region> newRegions = new HashMap<>();
      newRegions.put(randomUUID().toString(), toVGGRegion(label, confidenceAsDouble, null, p));
      if (vgg.containsKey(key)) {
        var annotation = vgg.get(key);
        newRegions.putAll(annotation.getRegions());
        annotation.setRegions(newRegions);
        vgg.put(key, annotation);
      }
      var annotation = VGG.Annotation.builder().filename(key).regions(newRegions).build();
      vgg.putIfAbsent(key, annotation);
    }
    return vgg;
  }

  public VGG from(Set<TiledPolygon> polygons) {
    var polygonsWithMetadata =
        polygons.stream()
            .map(
                p -> {
                  var metadata = new HashMap<>();
                  metadata.put("filename", filename(p.originTile()));
                  metadata.put("label", p.type().name());
                  var polygon = p.polygon();
                  polygon.setUserData(metadata);
                  return polygon;
                })
            .collect(Collectors.toSet());
    return convert(polygonsWithMetadata);
  }

  private static String filename(IntXY originTile) {
    return String.format("%s_%s_%s.jpg", 20, originTile.x(), originTile.y());
  }

  public Map<Feature, VGG> from(
      List<TiledPixelPolygon> tiledPixelPolygons,
      MultiPolygon roofLatLonMultiPolygon,
      List<TileCoordinates> envelop) {
    Map<Feature, List<TiledPixelPolygon>> tiledPixelPolygonFilteredByPoint =
        tiledPixelPolygons.stream().collect(Collectors.groupingBy(TiledPixelPolygon::point));
    var vggMap = new HashMap<Feature, VGG>();
    int minTileXGlobal = envelop.getFirst().getX();
    int minTileYGlobal = envelop.getFirst().getY();
    var tileCoordinates =
        tiledPixelPolygons.stream()
            .map(
                tiledPixelPolygon ->
                    new TileCoordinates()
                        .x(tiledPixelPolygon.tileX())
                        .y(tiledPixelPolygon.tileY())
                        .z(tiledPixelPolygon.zoom()))
            .toList();
    var roofPixelPolygon =
        tileCoordinates.stream()
            .map(
                coordinates -> {
                  var roofPixelIntersection =
                      tilePolygonIntersection.intersects(roofLatLonMultiPolygon, coordinates);
                  if (roofPixelIntersection.isEmpty()) {
                    return null;
                  }
                  var roofPolygonFromTile =
                      geometryConverter.convertToPolygon(roofPixelIntersection);
                  var projectedRoofPolygonToCompositeImage =
                      projectPolygonsToCompositeImage(
                          coordinates.getX(),
                          coordinates.getY(),
                          minTileXGlobal,
                          minTileYGlobal,
                          DEFAULT_IMG_SIZE,
                          roofPolygonFromTile);
                  return geometryFactory.createMultiPolygon(
                      new Polygon[] {projectedRoofPolygonToCompositeImage});
                })
            .filter(Objects::nonNull)
            .reduce(unifyMultiPolygon())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No roof pixel polygon retrieved from roofLatLonMultiPolygon : "
                            + roofLatLonMultiPolygon));

    Map<Feature, List<PolygonObjectType>> tiledPixelAllPolygonsByPoint =
        retrieveAllProjectedObjectsByFeature(tiledPixelPolygonFilteredByPoint);

    tiledPixelPolygonFilteredByPoint.forEach(
        (featurePoint, tiledPolygons) -> {
          var vgg = new VGG();
          // int minTileXForPoint =
          //    tiledPolygons.stream().mapToInt(TiledPixelPolygon::tileX).min().orElseThrow();
          // int minTileYForPoint =
          //    tiledPolygons.stream().mapToInt(TiledPixelPolygon::tileY).min().orElseThrow();
          tiledPolygons.forEach(
              tiledPolygon -> {
                var key =
                    String.format(
                        "%s_%s_%s_%s.jpg",
                        randomUUID(),
                        tiledPolygon.zoom(),
                        tiledPolygon.tileX(),
                        tiledPolygon.tileY());
                List<PolygonObjectType> originalPolygonObjectTypes = tiledPolygon.polygons();
                var projectedPolygonObjectTypes =
                    originalPolygonObjectTypes.stream()
                        .map(
                            polygonObjectType -> {
                              var detectedObjectPolygon = polygonObjectType.polygon();
                              var xCoordinates = getAllXCoordinates(detectedObjectPolygon);
                              var yCoordinates = getAllYCoordinates(detectedObjectPolygon);
                              if (xCoordinates.isEmpty() || yCoordinates.isEmpty()) {
                                return null;
                              }
                              var projectedPolygonsToCompositeImage =
                                  projectPolygonsToCompositeImage(
                                      tiledPolygon.tileX(),
                                      tiledPolygon.tileY(),
                                      minTileXGlobal,
                                      minTileYGlobal,
                                      DEFAULT_IMG_SIZE,
                                      detectedObjectPolygon);
                              return new PolygonObjectType(
                                  projectedPolygonsToCompositeImage,
                                  polygonObjectType.objectType());
                            })
                        .filter(Objects::nonNull)
                        .toList();

                Map<String, VGG.Annotation.Region> regions = new HashMap<>();
                if (!roofPixelPolygon.isEmpty()) {
                  regions.put(
                      String.valueOf(System.nanoTime()),
                      toVGGRegion(
                          TOITURE_REVETEMENT.name(),
                          null,
                          null,
                          (Polygon) roofPixelPolygon.getGeometryN(0)));
                }
                projectedPolygonObjectTypes.forEach(
                    polygonObjectType -> {
                      var detectedObjectPolygon = polygonObjectType.polygon();
                      var label = polygonObjectType.objectType();
                      var rate =
                          format(
                              (polygonObjectType.polygon().getArea() / roofPixelPolygon.getArea())
                                  * 100);
                      regions.put(
                          String.valueOf(System.nanoTime()),
                          toVGGRegion(label.name(), null, rate, detectedObjectPolygon));
                    });

                // TODO: improve duplicated loop performance
                var allPolygonObjectTypes =
                    tiledPixelAllPolygonsByPoint.getOrDefault(featurePoint, null);

                var properties =
                    computeProperties(
                        roofLatLonMultiPolygon, roofPixelPolygon, allPolygonObjectTypes);
                var annotation =
                    VGG.Annotation.builder()
                        .filename(key)
                        .properties(properties)
                        .regions(regions)
                        .build();
                vgg.putIfAbsent(key, annotation);
              });

          vggMap.put(featurePoint, vgg);
        });
    return vggMap;
  }

  private Map<Feature, List<PolygonObjectType>> retrieveAllProjectedObjectsByFeature(
      Map<Feature, List<TiledPixelPolygon>> tiledPixelPolygonFilteredByPoint) {
    return tiledPixelPolygonFilteredByPoint.entrySet().stream()
        .map(
            entry -> {
              var feature = entry.getKey();
              var tiledPolygons = entry.getValue();

              int minTileXForPoint =
                  tiledPolygons.stream().mapToInt(TiledPixelPolygon::tileX).min().orElseThrow();
              int minTileYForPoint =
                  tiledPolygons.stream().mapToInt(TiledPixelPolygon::tileY).min().orElseThrow();

              var featureMap = new HashMap<Feature, List<PolygonObjectType>>();
              var projectedPolygonObjectTypes =
                  tiledPolygons.stream()
                      .map(
                          tiledPolygon ->
                              tiledPolygon.polygons().stream()
                                  .map(
                                      polygonObjectType -> {
                                        var projectedPolygonsToCompositeImage =
                                            projectPolygonsToCompositeImage(
                                                tiledPolygon.tileX(),
                                                tiledPolygon.tileY(),
                                                minTileXForPoint,
                                                minTileYForPoint,
                                                DEFAULT_IMG_SIZE,
                                                polygonObjectType.polygon());
                                        return new PolygonObjectType(
                                            projectedPolygonsToCompositeImage,
                                            polygonObjectType.objectType());
                                      })
                                  .toList())
                      .flatMap(List::stream)
                      .toList();

              featureMap.put(feature, projectedPolygonObjectTypes);

              return featureMap;
            })
        .flatMap(map -> map.entrySet().stream())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (list1, list2) -> {
                  List<PolygonObjectType> merged = new ArrayList<>(list1);
                  merged.addAll(list2);
                  return merged;
                }));
  }

  private HashMap<String, Object> computeProperties(
      Geometry lonLatRoofPolygon,
      Geometry pixelRoofPolygon,
      List<PolygonObjectType> originalPolygonObjectTypes) {
    var rateComputer = new AreaRateComputerFacade(pixelRoofPolygon, originalPolygonObjectTypes);
    var usureRate = rateComputer.getUsureAreaRate();
    var humiditeRate = rateComputer.getHumidityAreaRate();
    var moisissureRate = rateComputer.getMoisissureAreaRate();
    var globalRateValue = rateComputer.getGlobalRate();
    var globalRateType = rateComputer.getRate();

    var properties = new HashMap<String, Object>();
    var dominantRoofs = new DominantRoof(originalPolygonObjectTypes).get();

    properties.put("roof_area_in_m2", geometrySquareMeterArea.apply(lonLatRoofPolygon));
    properties.put("usure_rate", usureRate);
    properties.put("humidite_rate", humiditeRate);
    properties.put("moisissure_rate", moisissureRate);
    properties.put("global_rate_value", globalRateValue);
    properties.put("global_rate_type", globalRateType);
    properties.put("revetement_1", dominantRoofs.greatest());
    properties.put("revetement_2", dominantRoofs.second());

    return properties;
  }

  private Polygon projectPolygonsToCompositeImage(
      Integer tileX,
      Integer tileY,
      int minTileX,
      int minTileY,
      int tileSize,
      Polygon originalPolygon) {
    int offsetX = (tileX - minTileX) * tileSize;
    int offsetY = (tileY - minTileY) * tileSize;
    return translatePolygon(originalPolygon, offsetX, offsetY);
  }

  private Polygon translatePolygon(Polygon polygon, int offsetX, int offsetY) {
    AffineTransformation translation = AffineTransformation.translationInstance(offsetX, offsetY);
    return (Polygon) translation.transform(polygon);
  }

  public VGG from(Polygon roofGeometry, DetectedTile detectedTile) {
    var vgg = new VGG();
    var originTile = detectedTile.getTile();
    var originTileCoords =
        new IntXY(originTile.getCoordinates().getX(), originTile.getCoordinates().getY());
    var tilingConf =
        new TilingConf(originTile.getCoordinates().getZ(), originTile.getSize().getHeight());
    var roofGeometryAsTile =
        new TiledPolygon(roofGeometry, null, originTileCoords, tilingConf)
            .latLonPolygon()
            .polygon();

    var roofAreaInM2 = geometrySquareMeterArea.apply(roofGeometryAsTile);
    var rateComputer = new AreaRateComputerFacade(roofGeometry, detectedTile);
    var detectedObjects = detectedTile.getDetectedObjects();
    var tile = detectedTile.getTile().getCoordinates();
    var key = String.format("%s_%s_%s_%s.jpg", randomUUID(), tile.getZ(), tile.getX(), tile.getY());

    var dominantRoofs = new DominantRoof(detectedTile).get();

    var usureRate = rateComputer.getUsureAreaRate();
    var humiditeRate = rateComputer.getHumidityAreaRate();
    var moisissureRate = rateComputer.getMoisissureAreaRate();
    var globalRateValue = rateComputer.getGlobalRate();
    var globalRateType = rateComputer.getRate();

    Map<String, VGG.Annotation.Region> regions = new HashMap<>();
    for (var object : detectedObjects) {
      var label = object.getDetectableObjectType();
      var confidence = object.getComputedConfidence();
      var polygon = featureMapper.toDomainPolygon(object.getFeature());
      var rate = format((polygon.getArea() / roofGeometry.getArea()) * 100);
      regions.put(
          String.valueOf(System.nanoTime()), toVGGRegion(label.name(), confidence, rate, polygon));
    }

    var properties = new HashMap<String, Object>();
    properties.put("roof_area_in_m2", roofAreaInM2);
    properties.put("revetement_1", dominantRoofs.greatest());
    properties.put("revetement_2", dominantRoofs.second());
    properties.put("usure_rate", usureRate);
    properties.put("humidite_rate", humiditeRate);
    properties.put("moisissure_rate", moisissureRate);
    properties.put("global_rate_value", globalRateValue);
    properties.put("global_rate_type", globalRateType);

    var annotation =
        VGG.Annotation.builder().filename(key).properties(properties).regions(regions).build();
    vgg.putIfAbsent(key, annotation);
    return vgg;
  }

  private VGG.Annotation.Region toVGGRegion(
      String label, Double confidence, Double rate, Polygon geometry) {
    List<Double> allX = getAllXCoordinates(geometry);
    List<Double> allY = getAllYCoordinates(geometry);
    var name = "Polygon";
    return VGG.Annotation.Region.builder()
        .regionAttribute(
            VGG.Annotation.Region.RegionAttribute.builder()
                .label(label.toUpperCase())
                .confidence(confidence)
                .rate_in_percent(rate)
                .build())
        .shapeAttribute(
            VGG.Annotation.Region.ShapeAttribute.builder()
                .name(name)
                .allPointsX(allX)
                .allPointsY(allY)
                .build())
        .build();
  }

  private List<Double> getAllYCoordinates(Polygon polygon) {
    return Arrays.stream(polygon.getCoordinates()).map(coor -> coor.y).toList();
  }

  private List<Double> getAllXCoordinates(Polygon polygon) {
    return Arrays.stream(polygon.getCoordinates()).map(coor -> coor.x).toList();
  }
}
