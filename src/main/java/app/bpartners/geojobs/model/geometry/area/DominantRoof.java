package app.bpartners.geojobs.model.geometry.area;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Polygon;

public class DominantRoof implements Supplier<DominantRoof.DominantDetectedRoof> {
  private static final String BATI_PREFIX = "BATI_";
  private final FeatureMapper featureMapper = new FeatureMapper(new GeometryConverter(null));
  private final Set<PolygonObjectType> polygons;

  public DominantRoof(DetectedTile detectedTiles) {
    this.polygons = filterRoof(detectedTiles);
  }

  public DominantRoof(List<PolygonObjectType> detected) {
    this.polygons = filterRoof(detected);
  }

  private Set<PolygonObjectType> filterRoof(List<PolygonObjectType> detected) {
    return detected.stream()
        .filter(o -> o.objectType().name().contains(BATI_PREFIX))
        .collect(Collectors.toSet());
  }

  private Set<PolygonObjectType> filterRoof(DetectedTile tile) {
    return tile.getDetectedObjects().stream()
        .map(
            o ->
                new PolygonObjectType(
                    featureMapper.toDomainPolygon(o.getFeature()), o.getDetectableObjectType()))
        .filter(o -> o.objectType().name().contains(BATI_PREFIX))
        .collect(Collectors.toSet());
  }

  @Override
  public DominantDetectedRoof get() {

    Map<DetectableType, List<PolygonObjectType>> allRoof =
        polygons.stream().collect(Collectors.groupingBy(PolygonObjectType::objectType));

    Map<DetectableType, Double> areas =
        allRoof.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e ->
                        e.getValue().stream()
                            .map(PolygonObjectType::polygon)
                            .mapToDouble(Polygon::getArea)
                            .sum()));

    List<Map.Entry<DetectableType, Double>> sorted =
        areas.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .toList();

    DetectableType first = !sorted.isEmpty() ? sorted.get(0).getKey() : null;
    DetectableType second = sorted.size() > 1 ? sorted.get(1).getKey() : null;

    return new DominantDetectedRoof(first, second);
  }

  public record DominantDetectedRoof(DetectableType greatest, DetectableType second) {}
}
