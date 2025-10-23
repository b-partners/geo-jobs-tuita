package app.bpartners.geojobs.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.area.AreaRateComputerFacade.*;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.*;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.getRoofMultiPolygon;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.toList;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.model.geometry.area.AreaRateComputerFacade;
import app.bpartners.geojobs.model.geometry.area.DominantRoof;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.event.DetectionRoofPropertiesRequestedService;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class VGGFactory implements Converter<Set<Polygon>, VGG> {
  private static final int DEFAULT_IMG_SIZE = 1024;
  private final FeatureMapper featureMapper;
  private final TileCoordinatesPolygonIntersection tilePolygonIntersection;
  private final GeometryConverter geometryConverter;
  private final GeometrySquareMeterArea geometrySquareMeterArea;
  private final ObjectMapper objectMapper;

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
    Map<Feature, List<TiledPixelPolygon>> tiledPixelPolygonGroupByFeature =
        tiledPixelPolygons.stream().collect(Collectors.groupingBy(TiledPixelPolygon::feature));
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
        retrieveAllProjectedObjectsByFeature(tiledPixelPolygonGroupByFeature);

    tiledPixelPolygonGroupByFeature.forEach(
        (featurePoint, tiledPolygons) -> {
          var vgg = new VGG();
          tiledPolygons.forEach(
              tiledPolygon -> {
                var key =
                    String.format(
                        "%s_%s_%s_%s.jpg",
                        System.nanoTime(),
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
                        roofLatLonMultiPolygon,
                        roofPixelPolygon,
                        new HashSet<>(allPolygonObjectTypes));
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

  public Map<Feature, VGG> from(
      List<TiledPixelPolygon> tiledPixelPolygons, List<TileCoordinates> envelop) {
    var vggMap = new HashMap<Feature, VGG>();
    int minTileXGlobal = envelop.getFirst().getX();
    int minTileYGlobal = envelop.getFirst().getY();

    var tiledPixelPolygonGroup =
        groupPixelPolygonByFeatureAndDetectableTypeAndTileCoordinates(tiledPixelPolygons);

    tiledPixelPolygonGroup.stream()
        .collect(Collectors.groupingBy(TiledPixelPolygonGrouped::feature))
        .forEach(
            (feature, tiledPixelPolygonGroupByFeature) -> {
              var vgg = new VGG();
              var roofMultiPolygon = getRoofMultiPolygon(feature);
              Map<String, VGG.Annotation.Region> regions = new HashMap<>();
              List<PolygonGroup> projectedPolygonGroups =
                  tiledPixelPolygonGroupByFeature.stream()
                      .flatMap(
                          tiledPixelPolygonGrouped -> {
                            var tileX = tiledPixelPolygonGrouped.tileX();
                            var tileY = tiledPixelPolygonGrouped.tileY();
                            var zoom = tiledPixelPolygonGrouped.zoom();
                            var polygonGroups = new ArrayList<>(tiledPixelPolygonGrouped.groups());
                            var polygonRoofPixelCoordinates =
                                tilePolygonIntersection.intersects(
                                    roofMultiPolygon, tileX, tileY, zoom);
                            if (!polygonRoofPixelCoordinates.isEmpty()) {
                              var polygonRoofPixelPolygon =
                                  geometryConverter.convertToPolygon(polygonRoofPixelCoordinates);
                              polygonGroups.add(
                                  new PolygonGroup(
                                      TOITURE_REVETEMENT, List.of(polygonRoofPixelPolygon)));
                            }
                            return polygonGroups.stream()
                                .map(
                                    polygonGroup -> {
                                      var detectableType = polygonGroup.objectType();
                                      var projectedPolygons =
                                          polygonGroup.polygons().stream()
                                              .map(
                                                  detectedObjectPolygon -> {
                                                    var xCoordinates =
                                                        getAllXCoordinates(detectedObjectPolygon);
                                                    var yCoordinates =
                                                        getAllYCoordinates(detectedObjectPolygon);
                                                    if (xCoordinates.isEmpty()
                                                        || yCoordinates.isEmpty()) {
                                                      return null;
                                                    }
                                                    return projectPolygonsToCompositeImage(
                                                        tileX,
                                                        tileY,
                                                        minTileXGlobal,
                                                        minTileYGlobal,
                                                        DEFAULT_IMG_SIZE,
                                                        detectedObjectPolygon);
                                                  })
                                              .filter(Objects::nonNull)
                                              .toList();
                                      return new PolygonGroup(detectableType, projectedPolygons);
                                    });
                          })
                      .collect(
                          Collectors.groupingBy(
                              PolygonGroup::objectType,
                              flatMapping(pg -> pg.polygons().stream(), toList())))
                      .entrySet()
                      .stream()
                      .map(e -> new PolygonGroup(e.getKey(), e.getValue()))
                      .toList();
              var polygonObjectTypesFromProjectedPolygonGroup = new ArrayList<PolygonObjectType>();
              projectedPolygonGroups.forEach(
                  polygonGroup -> {
                    var objectType = polygonGroup.objectType();
                    var label = objectType.name();
                    var polygons = new ArrayList<Polygon>();
                    var geometryUnified = polygonGroup.geometryUnified();
                    switch (geometryUnified) {
                      case null ->
                          log.warn(
                              "Unable to unify geometry for label {}, actual polygons {}",
                              label,
                              polygonGroup.polygons());
                      case Polygon polygon -> {
                        polygons.add(polygon);
                        regions.put(
                            String.valueOf(System.nanoTime()),
                            toVGGRegion(label, null, null, polygon));
                      }
                      case MultiPolygon multiPolygon -> {
                        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                          if (multiPolygon.getGeometryN(i) instanceof Polygon polygon) {
                            polygons.add(polygon);
                            regions.put(
                                String.valueOf(System.nanoTime()),
                                toVGGRegion(label, null, null, polygon));
                          }
                        }
                      }
                      default -> {}
                    }
                    var polygonObjectTypes =
                        polygons.stream()
                            .map(polygon -> new PolygonObjectType(polygon, objectType))
                            .toList();
                    polygonObjectTypesFromProjectedPolygonGroup.addAll(polygonObjectTypes);
                  });

              var detectedRoofCovering = retrieveCoveringProperties(feature);
              var addresses = retrieveAddressesProperty(feature);
              var roofPixelPolygonGroup =
                  projectedPolygonGroups.stream()
                      .filter(polygonGroup -> TOITURE_REVETEMENT.equals(polygonGroup.objectType()))
                      .findFirst()
                      .orElseThrow(
                          () ->
                              new UnsupportedOperationException(
                                  "No roof converted in pixel for feature " + feature));
              var roofPixelPolygonGeometry = roofPixelPolygonGroup.geometryUnified();
              var properties =
                  computeProperties(
                      feature,
                      roofMultiPolygon,
                      roofPixelPolygonGeometry,
                      detectedRoofCovering,
                      addresses,
                      polygonObjectTypesFromProjectedPolygonGroup);
              var key = randomUUID().toString();
              var annotation =
                  VGG.Annotation.builder()
                      .filename(key)
                      .properties(properties)
                      .regions(regions)
                      .build();

              vgg.putIfAbsent(key, annotation);

              vggMap.put(feature, vgg);
            });
    return vggMap;
  }

  private DetectionRoofPropertiesRequestedService.DetectedRoofCovering retrieveCoveringProperties(
      Feature feature) {
    if (feature.getProperties() == null
        || (feature.getProperties().isEmpty() || feature.getProperties().get("covering") == null)) {
      return null;
    }
    try {
      return objectMapper.readValue(
          feature.getProperties().get("covering").toString(),
          DetectionRoofPropertiesRequestedService.DetectedRoofCovering.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private List<String> retrieveAddressesProperty(Feature feature) {
    if (feature.getProperties() == null
        || (feature.getProperties().isEmpty()
            || feature.getProperties().get("addresses") == null)) {
      return null;
    }
    try {
      return objectMapper.readValue(
          feature.getProperties().get("addresses").toString(), new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  List<TiledPixelPolygonGrouped> groupPixelPolygonByFeatureAndDetectableTypeAndTileCoordinates(
      List<TiledPixelPolygon> input) {
    return input.stream()
        // 1. Grouper par feature + tileX + tileY + zoom
        .collect(
            Collectors.groupingBy(
                tpp -> new GroupKey(tpp.feature(), tpp.tileX(), tpp.tileY(), tpp.zoom()),
                flatMapping(tpp -> tpp.polygons().stream(), toList())))
        .entrySet()
        .stream()
        // 2. Pour chaque groupe, regrouper les Polygon par DetectableType
        .map(
            entry -> {
              GroupKey key = entry.getKey();
              List<PolygonGroup> groups =
                  entry.getValue().stream()
                      .collect(
                          Collectors.groupingBy(
                              PolygonObjectType::objectType,
                              Collectors.mapping(PolygonObjectType::polygon, toList())))
                      .entrySet()
                      .stream()
                      .map(e -> new PolygonGroup(e.getKey(), e.getValue()))
                      .toList();

              return new TiledPixelPolygonGrouped(
                  key.feature(), key.tileX(), key.tileY(), key.zoom(), groups);
            })
        .toList();
  }

  private Map<Feature, List<PolygonObjectType>> retrieveAllProjectedObjectsByFeature(
      Map<Feature, List<TiledPixelPolygon>> tiledPixelPolygonGroupByFeature) {
    return tiledPixelPolygonGroupByFeature.entrySet().stream()
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
      Geometry roofPixelPolygon,
      Set<PolygonObjectType> originalPolygonObjectTypes) {
    var rateComputer = new AreaRateComputerFacade(roofPixelPolygon, originalPolygonObjectTypes);
    var usureRate = rateComputer.getUsureAreaRate();
    var humiditeRate = rateComputer.getHumidityAreaRate();
    var moisissureRate = rateComputer.getMoisissureAreaRate();
    var globalRateValue = rateComputer.getGlobalRate();
    var globalRateType = rateComputer.getRate();

    var properties = new HashMap<String, Object>();
    var dominantRoofs = new DominantRoof(originalPolygonObjectTypes.stream().toList()).get();

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

  private HashMap<String, Object> computeProperties(
      Feature feature,
      Geometry lonLatRoofPolygon,
      Geometry roofPixelPolygon,
      DetectionRoofPropertiesRequestedService.DetectedRoofCovering detectedRoofCovering,
      List<String> addresses,
      Collection<PolygonObjectType> polygonObjectTypes) {
    var rateComputer = new AreaRateComputerFacade(roofPixelPolygon, polygonObjectTypes);
    var usureRate = rateComputer.getUsureAreaRate();
    var humiditeRate = rateComputer.getHumidityAreaRate();
    var moisissureRate = rateComputer.getMoisissureAreaRate();
    var globalRateValue = rateComputer.getGlobalRate();
    var globalRateType = rateComputer.getRate();

    var featureProperties =
        feature.getProperties() == null ? new HashMap<String, Object>() : feature.getProperties();
    var properties = new HashMap<String, Object>();

    properties.put("roof_area_in_m2", geometrySquareMeterArea.apply(lonLatRoofPolygon));
    properties.put("usure_rate", usureRate);
    properties.put("humidite_rate", humiditeRate);
    properties.put("moisissure_rate", moisissureRate);
    properties.put("global_rate_value", globalRateValue);
    properties.put("global_rate_type", globalRateType);
    properties.put("addresses", addresses);

    // Lidar properties
    if (featureProperties.containsKey(LIDAR_DATA_STATUS_PROPERTY_NAME)) {
      properties.put("roof_slope_in_degrees", featureProperties.get(ROOF_SLOPE_PROPERTY_NAME));
      properties.put("roof_height_in_meters", featureProperties.get(ROOF_HEIGHT_PROPERTY_NAME));
      properties.put(
          "roof_slope_data_status", featureProperties.get(LIDAR_DATA_STATUS_PROPERTY_NAME));
      properties.put(
          "roof_height_data_status", featureProperties.get(LIDAR_DATA_STATUS_PROPERTY_NAME));
    }

    if (detectedRoofCovering != null) {
      properties.put(
          "revetement_1",
          detectedRoofCovering.primary() == null ? null : detectedRoofCovering.primary().name());
      properties.put(
          "revetement_2",
          detectedRoofCovering.secondary() == null
              ? null
              : detectedRoofCovering.secondary().name());
    }
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
      String label, Double confidence, Double rate, Polygon polygon) {
    List<Double> allX = getAllXCoordinates(polygon);
    List<Double> allY = getAllYCoordinates(polygon);
    var name = "Polygon";
    HashMap<String, Object> regionAttributes = new HashMap<>();
    if (label != null) {
      regionAttributes.put("label", label.toUpperCase());
    }
    if (confidence != null) {
      regionAttributes.put("confidence", confidence);
    }
    if (rate != null) {
      regionAttributes.put("rate_in_percent", rate);
    }
    return VGG.Annotation.Region.builder()
        .regionAttribute(regionAttributes)
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

  record PolygonGroup(DetectableType objectType, List<Polygon> polygons) {
    public Geometry geometryUnified() {
      if (polygons == null || polygons.isEmpty()) return null;
      GeometryCollection geometryCollection =
          new GeometryCollection(polygons().toArray(new Polygon[0]), geometryFactory);
      return geometryCollection.union();
    }
  }

  record TiledPixelPolygonGrouped(
      Feature feature, int tileX, int tileY, int zoom, List<PolygonGroup> groups) {}

  record GroupKey(Feature feature, int tileX, int tileY, int zoom) {}
}
