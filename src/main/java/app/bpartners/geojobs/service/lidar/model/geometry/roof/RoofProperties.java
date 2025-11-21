package app.bpartners.geojobs.service.lidar.model.geometry.roof;

import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.*;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import app.bpartners.geojobs.service.lidar.model.geometry.planes.Planes3DExtractor;
import app.bpartners.geojobs.service.lidar.preprocessing.ground.GroundPointsCleaner;
import app.bpartners.geojobs.service.lidar.preprocessing.roof.RoofPointsCleaner;
import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

@Slf4j
@RequiredArgsConstructor
public class RoofProperties {
  @Getter private final LidarRoofData data;

  // properties
  private List<RoofPlane3D> planes;
  private RoofHeightInMeters roofHeightInMeters;

  // cleaned data
  private Set<LasPointGeometry> cleanedRoofPoints;
  private Set<LasPointGeometry> cleanedGroundPoints;

  private static final double POINTS_THRESHOLD = 0.2;
  private static final double POINTS_CONTINUATION_THRESHOLD = 1;

  private static final int MINIMUM_PLANE_POINTS_COUNT = 10;

  private static final short MINIMUM_ROOF_POINTS_COUNT = 5;
  private static final short MINIMUM_GROUND_POINTS_COUNT = 5;

  public RoofHeightInMeters getHeightInMeters() {
    if (hasInvalidData()) {
      return new RoofHeightInMeters(List.of(), List.of());
    }

    if (roofHeightInMeters == null) {
      roofHeightInMeters = new RoofHeightInMeters(getCleanedRoofPoints(), getCleanedGroundPoints());
    }

    return roofHeightInMeters;
  }

  public List<RoofPlane3D> getPlanes() {
    if (hasInvalidData()) {
      return List.of();
    }

    if (planes == null) {
      var extractor =
          new Planes3DExtractor(
              POINTS_THRESHOLD, POINTS_CONTINUATION_THRESHOLD, MINIMUM_PLANE_POINTS_COUNT);
      var rawPlanes = extractor.apply(data.roof().points());
      planes =
          rawPlanes.stream()
              .map(plane -> new RoofPlane3D(toPolygon(data.roof().boundaryLambert93()), plane))
              .toList();
    }

    return planes;
  }

  public boolean hasInvalidData() {
    if (!AVAILABLE.equals(data.status())) {
      return true;
    }

    if (data.roof().points().size() < MINIMUM_ROOF_POINTS_COUNT) {
      return true;
    }

    return data.ground().points().size() < MINIMUM_GROUND_POINTS_COUNT;
  }

  public Set<LasPointGeometry> getCleanedRoofPoints() {
    if (cleanedRoofPoints == null) {
      var cleaner = new RoofPointsCleaner();
      cleanedRoofPoints = cleaner.apply(data.roof().points());
    }
    return cleanedRoofPoints;
  }

  public Set<LasPointGeometry> getCleanedGroundPoints() {
    if (cleanedGroundPoints == null) {
      var cleaner = new GroundPointsCleaner();
      cleanedGroundPoints = cleaner.apply(data.ground().points());
    }
    return cleanedGroundPoints;
  }

  private static Polygon toPolygon(Geometry geometry) {
    return switch (geometry) {
      case Polygon polygon -> polygon;
      case MultiPolygon multiPolygon -> {
        if (1 != multiPolygon.getNumGeometries()) {
          log.warn("Unsupported polygon type");
        }
        yield (Polygon) multiPolygon.getGeometryN(0);
      }
      default -> throw new IllegalArgumentException("Unexpected type retrieved");
    };
  }
}
