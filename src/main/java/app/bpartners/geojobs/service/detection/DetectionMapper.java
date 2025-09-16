package app.bpartners.geojobs.service.detection;

import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.*;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.*;
import static app.bpartners.geojobs.model.CustomObjectMapper.objectMapper;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static app.bpartners.geojobs.repository.model.GeoJobType.DETECTION;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.HUMAN;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.MACHINE;
import static app.bpartners.geojobs.service.detection.DetectionResponse.REGION_CONFIDENCE_PROPERTY;
import static app.bpartners.geojobs.service.detection.DetectionResponse.REGION_LABEL_PROPERTY;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;

import app.bpartners.gen.annotator.endpoint.rest.model.Annotation;
import app.bpartners.gen.annotator.endpoint.rest.model.Label;
import app.bpartners.gen.annotator.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.*;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.tiling.TileValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class DetectionMapper {
  public static final String TOITURE_REVETEMENT_STRING_VALUE = "TOITURE_REVETEMENT";
  public static final String PANNEAU_PHOTOVOLTAIQUE_STRING_VALUE = "PANNEAU_PHOTOVOLTAIQUE";
  public static final String ARBRE_STRING_VALUE = "ARBRE";
  public static final String PASSAGE_PIETON_STRING_VALUE = "PASSAGE_PIETON";
  public static final String PISCINE_STRING_VALUE = "PISCINE";
  public static final String BATI_TUILES_STRING_VALUE = "BATI_TUILES";
  public static final String BATI_BETON_STRING_VALUE = "BATI_BETON";
  public static final String BATI_ARDOISE_STRING_VALUE = "BATI_ARDOISE";
  public static final String BATI_AUTRES_STRING_VALUE = "BATI_AUTRES";
  public static final String LINE_STRING_VALUE = "LINE";
  public static final String TROTTOIR_STRING_VALUE = "TROTTOIR";
  public static final String PARKING_STRING_VALUE = "PARKING";
  public static final String ESPACE_VERT_STRING_VALUE = "ESPACE_VERT";
  public static final String OBSTACLE_STRING_VALUE = "OBSTACLE";
  public static final String CHEMINEE_STRING_VALUE = "CHEMINEE";
  public static final String VELUX_STRING_VALUE = "VELUX";

  public static final String USURE_IMPORTANTE_ARDOISE_STRING_VALUE = "USURE_IMPORTANTE_ARDOISE";
  public static final String USURE_IMPORTANTE_TUILES_STRING_VALUE = "USURE_IMPORTANTE_TUILES";
  public static final String USURE_LEGERE_ARDOISE_STRING_VALUE = "USURE_LEGERE_ARDOISE";
  public static final String USURE_LEGERE_AUTRES_STRING_VALUE = "USURE_LEGERE_AUTRES";

  public static final String MOISISSURE_COULEUR_ARDOISE_STRING_VALUE = "MOISISSURE_COULEUR_ARDOISE";
  public static final String MOISISSURE_COULEUR_TUILES_STRING_VALUE = "MOISISSURE_COULEUR_TUILES";
  public static final String MOISISSURE_NOIRCIE_TUILES_STRING_VALUE = "MOISISSURE_NOIRCIE_TUILES";
  public static final String MOISISSURE_CLAIR_TUILES_STRING_VALUE = "MOISISSURE_CLAIR_TUILES";

  public static final String HUMIDITE_CLAIR_AUTRES_STRING_VALUE = "HUMIDITE_CLAIR_AUTRES";
  public static final String HUMIDITE_INTENSE_AUTRES_STRING_VALUE = "HUMIDITE_INTENSE_AUTRES";

  public static final String ROOF_STRING_VALUE = "ROOF";
  public static final String ROOF_ARDOISE_STRING_VALUE = "ROOF_ARDOISE";
  public static final String ROOF_AUTRES_STRING_VALUE = "ROOF_AUTRES";
  public static final String ROOF_TUILES_STRING_VALUE = "ROOF_TUILES";

  public static final String SOLAR_PANEL_STRING_VALUE = "SOLAR_PANEL";
  public static final String PV_STRING_VALUE = "PV";

  public static final String TREE_STRING_VALUE = "TREE";
  public static final String PATHWAY_STRING_VALUE = "PATHWAY";
  public static final String POOL_STRING_VALUE = "POOL";

  private final TileValidator tileValidator;

  public MachineDetectedTile toDetectedTile(
      DetectionResponse detectionResponse,
      Tile tile,
      String parcelId,
      String zdjJobId,
      String parcelJobId) {
    String detectedTileId = randomUUID().toString();
    var tileCoordinates = tile.getCoordinates();
    tileValidator.accept(tile);

    var fileData = detectionResponse.getRstRaw().values().stream().toList().getFirst();

    List<DetectionResponse.ImageData.Region> regions =
        fileData.getRegions().values().stream().toList();
    List<DetectedObject> machineDetectedObjects =
        regions.stream()
            .map(region -> toDetectedObject(region, detectedTileId, tileCoordinates.getZ()))
            .toList();

    return MachineDetectedTile.builder()
        .id(detectedTileId)
        .zdjJobId(zdjJobId)
        .parcelJobId(parcelJobId)
        .parcelId(parcelId)
        .tile(tile)
        .bucketPath(tile.getBucketPath())
        .detectedObjects(machineDetectedObjects)
        .creationDatetime(now())
        .build();
  }

  public DetectedObject toDetectedObject(
      DetectionResponse.ImageData.Region region, String detectedTileId, Integer zoom) {
    var regionAttributes = region.getRegionAttributes();
    var label = regionAttributes.get(REGION_LABEL_PROPERTY);
    Double confidence = null;
    try {
      if (regionAttributes.containsKey(REGION_CONFIDENCE_PROPERTY)) {
        confidence = Double.valueOf(regionAttributes.get(REGION_CONFIDENCE_PROPERTY));
      }

    } catch (NumberFormatException ignored) {
    }
    var polygon = region.getShapeAttributes();
    var objectId = randomUUID().toString();
    return DetectedObject.builder()
        .id(objectId)
        .detectedTileId(detectedTileId)
        .type(MACHINE)
        .detectedObjectType(
            DetectableObjectType.builder()
                .id(randomUUID().toString())
                .objectId(objectId)
                .detectableType(toDetectableType(label))
                .build())
        .feature(toFeature(polygon, zoom))
        .computedConfidence(confidence)
        .build();
  }

  private DetectableType toDetectableType(String label) {
    return switch (label.toUpperCase()) {
      case ROOF_STRING_VALUE, TOITURE_REVETEMENT_STRING_VALUE -> DetectableType.TOITURE_REVETEMENT;
      case SOLAR_PANEL_STRING_VALUE, PV_STRING_VALUE, PANNEAU_PHOTOVOLTAIQUE_STRING_VALUE ->
          DetectableType.PANNEAU_PHOTOVOLTAIQUE;
      case TREE_STRING_VALUE, ARBRE_STRING_VALUE -> DetectableType.ARBRE;
      case PATHWAY_STRING_VALUE, PASSAGE_PIETON_STRING_VALUE -> DetectableType.PASSAGE_PIETON;
      case POOL_STRING_VALUE, PISCINE_STRING_VALUE -> DetectableType.PISCINE;
      case BATI_TUILES_STRING_VALUE, ROOF_TUILES_STRING_VALUE -> DetectableType.BATI_TUILES;
      case BATI_BETON_STRING_VALUE -> DetectableType.BATI_BETON;
      case BATI_ARDOISE_STRING_VALUE, ROOF_ARDOISE_STRING_VALUE -> DetectableType.BATI_ARDOISE;
      case BATI_AUTRES_STRING_VALUE, ROOF_AUTRES_STRING_VALUE -> DetectableType.BATI_AUTRES;
      case LINE_STRING_VALUE -> DetectableType.LINE;
      case TROTTOIR_STRING_VALUE -> DetectableType.TROTTOIR;
      case PARKING_STRING_VALUE -> DetectableType.PARKING;
      case ESPACE_VERT_STRING_VALUE -> DetectableType.ESPACE_VERT;
      case OBSTACLE_STRING_VALUE -> DetectableType.OBSTACLE;
      case CHEMINEE_STRING_VALUE -> DetectableType.CHEMINEE;
      case VELUX_STRING_VALUE -> DetectableType.VELUX;
      case USURE_IMPORTANTE_ARDOISE_STRING_VALUE, USURE_IMPORTANTE_TUILES_STRING_VALUE ->
          DetectableType.USURE_IMPORTANTE;
      case USURE_LEGERE_ARDOISE_STRING_VALUE, USURE_LEGERE_AUTRES_STRING_VALUE ->
          DetectableType.USURE_LEGER;
      case MOISISSURE_COULEUR_ARDOISE_STRING_VALUE, MOISISSURE_COULEUR_TUILES_STRING_VALUE ->
          DetectableType.MOISISSURE_COULEUR;
      case MOISISSURE_NOIRCIE_TUILES_STRING_VALUE -> DetectableType.MOISISSURE_NOIRCIE;
      case MOISISSURE_CLAIR_TUILES_STRING_VALUE -> DetectableType.MOISISSURE_CLAIR;
      case HUMIDITE_CLAIR_AUTRES_STRING_VALUE -> DetectableType.HUMIDITE_CLAIR;
      case HUMIDITE_INTENSE_AUTRES_STRING_VALUE -> DetectableType.HUMIDITE_INTENSE;
      default -> throw new IllegalStateException("Unexpected value: " + label.toLowerCase());
    };
  }

  @SneakyThrows
  private app.bpartners.geojobs.repository.model.Feature toFeature(
      DetectionResponse.ImageData.ShapeAttributes shapeAttributes, int zoom) {
    List<List<BigDecimal>> coordinates = new ArrayList<>();
    var allX =
        shapeAttributes.getAllPointsX().stream().map(x -> new BigDecimal(x.intValue())).toList();
    var allY =
        shapeAttributes.getAllPointsY().stream().map(y -> new BigDecimal(y.intValue())).toList();
    IntStream.range(0, allX.size())
        .forEach(i -> coordinates.add(List.of(allX.get(i), allY.get(i))));
    var featureId = randomUUID().toString();
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("id", featureId);
    properties.put("zoom", zoom);
    return app.bpartners.geojobs.repository.model.Feature.builder()
        .id(featureId)
        .zoom(zoom)
        .geometry(
            app.bpartners.geojobs.repository.model.Feature.FeatureGeometry.builder()
                .geometryType(MULTI_POLYGON)
                .actualInstanceStringValue(
                    objectMapper()
                        .writeValueAsString(
                            new MultiPolygon()
                                .type(MultiPolygon.TypeEnum.MULTI_POLYGON)
                                .coordinates(List.of(List.of(coordinates)))))
                .build())
        .properties(properties)
        .build();
  }

  public ZoneDetectionJob fromTilingJob(ZoneTilingJob tilingJob) {
    String zoneDetectionJobId = randomUUID().toString();
    var detectionJob =
        ZoneDetectionJob.builder()
            .id(zoneDetectionJobId)
            .zoneTilingJob(tilingJob)
            .detectionType(MACHINE)
            .zoneName(tilingJob.getZoneName())
            .emailReceiver(tilingJob.getEmailReceiver())
            .submissionInstant(now())
            .build();
    detectionJob.hasNewStatus(
        JobStatus.builder()
            .jobId(zoneDetectionJobId)
            .id(randomUUID().toString())
            .creationDatetime(now())
            .jobType(DETECTION)
            .progression(PENDING)
            .health(UNKNOWN)
            .build());
    return detectionJob;
  }

  public Status.ProgressionStatus getProgressionStatus(
      app.bpartners.gen.annotator.endpoint.rest.model.JobStatus annotationJobStatus) {
    if (annotationJobStatus == null) return null;
    return switch (annotationJobStatus.getValue()) {
      case "COMPLETED", "FAILED" -> FINISHED;
      case "STARTED" -> PROCESSING;
      case "PENDING", "READY", "TO_REVIEW", "TO_CORRECT" -> PENDING;
      default ->
          throw new ApiException(
              SERVER_EXCEPTION, "Unknown annotationJobStatus " + annotationJobStatus.getValue());
    };
  }

  public Status.HealthStatus getHealthStatus(
      app.bpartners.gen.annotator.endpoint.rest.model.JobStatus annotationJobStatus) {
    if (annotationJobStatus == null) return null;
    return switch (annotationJobStatus.getValue()) {
      case "COMPLETED" -> SUCCEEDED;
      case "FAILED" -> FAILED;
      default -> UNKNOWN;
    };
  }

  public List<DetectedObject> toHumanDetectedObject(
      int zoom, String tileId, List<Annotation> annotations) {
    return annotations.stream()
        .map(
            ann -> {
              var objectId = randomUUID().toString();
              if (ann.getPolygon() == null) return null;
              var confidence = ann.getComment() != null ? getConfidence(ann.getComment()) : null;
              return DetectedObject.builder()
                  .id(objectId)
                  .detectedObjectType(toDetectableObjectType(objectId, ann.getLabel()))
                  .computedConfidence(confidence)
                  .feature(toFeature(zoom, ann.getPolygon()))
                  .detectedTileId(tileId)
                  .type(HUMAN)
                  .build();
            })
        .toList();
  }

  private DetectableObjectType toDetectableObjectType(String objectId, Label label) {
    if (label.getName() == null) {
      throw new IllegalArgumentException("label.name cannot be null");
    }
    return switch (label.getName().toUpperCase()) {
      case TOITURE_REVETEMENT_STRING_VALUE -> create(objectId, TOITURE_REVETEMENT);
      case PANNEAU_PHOTOVOLTAIQUE_STRING_VALUE -> create(objectId, PANNEAU_PHOTOVOLTAIQUE);
      case ARBRE_STRING_VALUE -> create(objectId, ARBRE);
      case PASSAGE_PIETON_STRING_VALUE -> create(objectId, PASSAGE_PIETON);
      case PISCINE_STRING_VALUE -> create(objectId, PISCINE);
      case BATI_TUILES_STRING_VALUE -> create(objectId, BATI_TUILES);
      case BATI_BETON_STRING_VALUE -> create(objectId, BATI_BETON);
      case BATI_ARDOISE_STRING_VALUE -> create(objectId, BATI_ARDOISE);
      case BATI_AUTRES_STRING_VALUE -> create(objectId, BATI_AUTRES);
      case LINE_STRING_VALUE -> create(objectId, LINE);
      case TROTTOIR_STRING_VALUE -> create(objectId, TROTTOIR);
      case PARKING_STRING_VALUE -> create(objectId, PARKING);
      case ESPACE_VERT_STRING_VALUE -> create(objectId, ESPACE_VERT);
      default ->
          throw new IllegalStateException("Unexpected value: " + label.getName().toUpperCase());
    };
  }

  private double getConfidence(String comment) {
    var splitInput = Arrays.stream(comment.split("=")).toList();
    return new BigDecimal(splitInput.getLast()).doubleValue() / 100;
  }

  private DetectableObjectType create(String objectId, DetectableType detectableType) {
    return DetectableObjectType.builder()
        .id(randomUUID().toString())
        .objectId(objectId)
        .detectableType(detectableType)
        .build();
  }

  @SneakyThrows
  private app.bpartners.geojobs.repository.model.Feature toFeature(int zoom, Polygon polygon) {
    if (polygon.getPoints() == null) return null;
    var coordinates =
        polygon.getPoints().stream()
            .map(
                point -> {
                  if (point.getX() == null || point.getY() == null) return null;
                  return List.of(
                      List.of(
                          List.of(
                              BigDecimal.valueOf(point.getX()), BigDecimal.valueOf(point.getY()))));
                })
            .toList();
    var featureId = randomUUID().toString();
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("zoom", zoom);
    properties.put("id", featureId);
    return Feature.builder()
        .id(featureId)
        .zoom(zoom)
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(MULTI_POLYGON)
                .actualInstanceStringValue(
                    objectMapper()
                        .writeValueAsString(
                            new MultiPolygon()
                                .coordinates(coordinates)
                                .type(MultiPolygon.TypeEnum.MULTI_POLYGON)))
                .build())
        .properties(properties)
        .build();
  }
}
