package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;

import app.bpartners.geojobs.endpoint.event.model.DetectionRoofPropertiesRequested;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.repository.model.detection.MachineDetectedTile;
import app.bpartners.geojobs.repository.model.detection.RoofCoveringType;
import app.bpartners.geojobs.service.detection.RoofCovering;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DetectionRoofPropertiesRequestedService
    implements Consumer<DetectionRoofPropertiesRequested> {
  private final DetectionRepository detectionRepository;
  private final MachineDetectedTileRepository machineDetectedTileRepository;
  private final GeometryConverter geometryConverter;
  private final ObjectMapper objectMapper;

  @Override
  public void accept(DetectionRoofPropertiesRequested event) {
    var detectionIdentifier = event.getDetectionIdentifier();
    var detection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    var machineDetectedTiles =
        machineDetectedTileRepository.findAllByZdjJobId(detection.getZdjId());
    var detectionWithRoofProperties = apply(detection, machineDetectedTiles);
    detectionRepository.save(detectionWithRoofProperties);
  }

  public Detection apply(Detection detection, List<MachineDetectedTile> machineDetectedTiles) {
    var featureWithDelimitationsCovering =
        detection.getFeatureWithDelimitations().stream()
            .map(
                featureWithDelimitation ->
                    applyRoofPropertiesOnDelimitation(
                        machineDetectedTiles, featureWithDelimitation))
            .toList();
    return detection.toBuilder().featureWithDelimitations(featureWithDelimitationsCovering).build();
  }

  public FeatureWithDelimitation applyRoofPropertiesOnDelimitation(
      List<MachineDetectedTile> machineDetectedTiles,
      FeatureWithDelimitation featureWithDelimitation) {
    var roofFeatures = featureWithDelimitation.getRestDelimitations();
    var domainFeaturesWithCovering =
        roofFeatures.stream()
            .map(
                roofFeature -> {
                  var geometryType = roofFeature.getGeometry().getActualInstance();
                  MultiPolygon roofLatLonMultiPolygon;
                  switch (geometryType) {
                    case Polygon polygon ->
                        roofLatLonMultiPolygon =
                            geometryConverter.apply(List.of(polygon.getCoordinates()));
                    case app.bpartners.geojobs.endpoint.rest.model.MultiPolygon multiPolygon ->
                        roofLatLonMultiPolygon =
                            geometryConverter.apply(multiPolygon.getCoordinates());
                    default ->
                        throw new UnsupportedOperationException(
                            "Unsupported geometry type for roof: " + geometryType);
                  }
                  Map<RoofCoveringType, Long> summedAreas =
                      machineDetectedTiles.stream()
                          .filter(
                              detectedTile -> {
                                var tileCoordinates = detectedTile.getTile().getCoordinates();
                                var multiPolygonFromTile =
                                    geometryConverter.getMultiPolygonFromTile(
                                        tileCoordinates.getX(),
                                        tileCoordinates.getY(),
                                        tileCoordinates.getZ());
                                return multiPolygonFromTile.intersects(roofLatLonMultiPolygon);
                              })
                          .map(
                              detectedTile ->
                                  new DetectedRoofCoveringArea(
                                      new RoofCovering(
                                          detectedTile.getPrimaryRoofCoveringType(),
                                          detectedTile.getPrimaryRoofCoveringArea()),
                                      new RoofCovering(
                                          detectedTile.getSecondaryRoofCoveringType(),
                                          detectedTile.getSecondaryRoofCoveringArea())))
                          .flatMap(
                              d ->
                                  Stream.concat(
                                      Stream.ofNullable(d.primary()),
                                      Stream.ofNullable(d.secondary())))
                          .filter(o -> o != null && (o.coating() != null && o.area() != null))
                          .collect(
                              Collectors.groupingBy(
                                  RoofCovering::coating,
                                  Collectors.summingLong(RoofCovering::area)));
                  List<Map.Entry<RoofCoveringType, Long>> sorted =
                      summedAreas.entrySet().stream()
                          .sorted(Map.Entry.<RoofCoveringType, Long>comparingByValue().reversed())
                          .toList();
                  RoofCoveringType primary = null;
                  RoofCoveringType secondary = null;
                  if (!sorted.isEmpty()) {
                    primary = sorted.getFirst().getKey();
                  }
                  if (sorted.size() > 1) {
                    secondary = sorted.get(1).getKey();
                  }
                  Map<String, Object> properties =
                      roofFeature.getProperties() == null
                          ? new HashMap<>()
                          : roofFeature.getProperties();
                  try {
                    properties.put(
                        "covering",
                        objectMapper.writeValueAsString(
                            new DetectedRoofCovering(primary, secondary)));
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                  return toDomainFeature(
                      new Feature()
                          .geometry(roofFeature.getGeometry())
                          .type(roofFeature.getType())
                          .properties(properties));
                })
            .toList();
    return new FeatureWithDelimitation(
        featureWithDelimitation.feature(), domainFeaturesWithCovering);
  }

  private record DetectedRoofCoveringArea(RoofCovering primary, RoofCovering secondary) {}

  public static final class DetectedRoofCovering {
    @JsonProperty private final RoofCoveringType primary;
    @JsonProperty private final RoofCoveringType secondary;

    public DetectedRoofCovering() {
      this(null, null);
    }

    public DetectedRoofCovering(RoofCoveringType primary, RoofCoveringType secondary) {
      this.primary = primary;
      this.secondary = secondary;
    }

    public RoofCoveringType primary() {
      return primary;
    }

    public RoofCoveringType secondary() {
      return secondary;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (DetectedRoofCovering) obj;
      return Objects.equals(this.primary, that.primary)
          && Objects.equals(this.secondary, that.secondary);
    }

    @Override
    public int hashCode() {
      return Objects.hash(primary, secondary);
    }

    @Override
    public String toString() {
      return "DetectedRoofCovering[" + "primary=" + primary + ", " + "secondary=" + secondary + ']';
    }
  }
}
