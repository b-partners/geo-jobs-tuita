package app.bpartners.geojobs.service.lidar;

import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;

import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.lidar.model.*;
import com.github.mreutegg.laszip4j.LASReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LidarPolygonMetricProcessor implements Function<List<Polygon>, List<Dimension>> {
  private final LidarApi lidarApi;
  private final GeometrySquareMeterArea projector;
  private static final int SOL_BUFFER_METERS = 3;
  private static final short SOL_LIDAR_CLASS_VALUE = 2;
  private static final short BATIMENT_LIDAR_CLASS_VALUE = 6;

  @Override
  public List<Dimension> apply(List<Polygon> roofGeometries) {
    var projectedRoofGeometries =
        roofGeometries.stream().map(g -> projector.project(g, WGS84, LAMBERT_93)).toList();
    var downloadedLidarFiles = lidarApi.apply(projectedRoofGeometries);
    var solGeometries =
        projectedRoofGeometries.stream().map(g -> g.buffer(SOL_BUFFER_METERS)).toList();

    var dimensions =
        getDimensionsFromMultipleFiles(
            projectedRoofGeometries, solGeometries, downloadedLidarFiles);

    cleanUpLidarFiles(downloadedLidarFiles);

    return dimensions;
  }

  public static void cleanUpLidarFiles(Set<File> files) {
    log.info("Cleanup lidar files");
    for (var file : files) {
      try {
        Files.deleteIfExists(file.toPath());
      } catch (IOException e) {
        log.warn("Failed to delete file {}", file.getPath(), e);
      }
    }
  }

  private List<Dimension> getDimensionsFromMultipleFiles(
      List<Geometry> roofGeometries, List<Geometry> solGeometries, Set<File> lidarFiles) {
    List<Dimension> results = roofGeometries.stream().map(g -> Dimension.empty()).toList();

    var dimensionsPerFile =
        lidarFiles.parallelStream()
            .map(file -> getDimensions(roofGeometries, solGeometries, file))
            .toList();

    for (var dimensions : dimensionsPerFile) {
      for (int i = 0; i < results.size(); i++) {
        results.get(i).roof().addAll(dimensions.get(i).roof().points());
        results.get(i).sol().addAll(dimensions.get(i).sol().points());
      }
    }

    return results;
  }

  private List<Dimension> getDimensions(
      List<Geometry> roofGeometries, List<Geometry> solGeometries, File file) {
    List<Dimension> dimensions = roofGeometries.stream().map(g -> Dimension.empty()).toList();
    log.info("Reading lasPoints from: {}", file.getPath());
    var lasReader = new LASReader(file);
    var lasHeader = lasReader.getHeader();

    for (var point : lasReader.getPoints()) {
      var pointClassification = point.getClassification();

      switch (pointClassification) {
        case SOL_LIDAR_CLASS_VALUE:
          var solPoint = new LasPointGeometry(point, lasHeader);
          handleSolPoint(solPoint, solGeometries, dimensions);
          break;
        case BATIMENT_LIDAR_CLASS_VALUE:
          var roofPoint = new LasPointGeometry(point, lasHeader);
          handleRoofPoint(roofPoint, roofGeometries, dimensions);
          break;
        default:
          break;
      }
    }

    log.info("Finished reading lasPoints from: {}", file.getPath());
    return dimensions;
  }

  void handleSolPoint(
      LasPointGeometry solPoint, List<Geometry> solGeometries, List<Dimension> dimensions) {
    for (int i = 0; i < solGeometries.size(); i++) {
      var dimension = dimensions.get(i);
      var solGeometry = solGeometries.get(i);

      if (isOutsideEnvelopeOfGeometry(solPoint, solGeometry)) {
        continue;
      }

      if (solGeometry.contains(solPoint)) {
        dimension.sol().add(solPoint);
        break;
      }
    }
  }

  void handleRoofPoint(
      LasPointGeometry roofPoint, List<Geometry> roofGeometries, List<Dimension> dimensions) {
    for (int i = 0; i < roofGeometries.size(); i++) {
      var dimension = dimensions.get(i);
      var roofGeometry = roofGeometries.get(i);

      if (isOutsideEnvelopeOfGeometry(roofPoint, roofGeometry)) {
        continue;
      }

      if (roofGeometry.contains(roofPoint)) {
        dimension.roof().add(roofPoint);
        break;
      }
    }
  }

  private static boolean isOutsideEnvelopeOfGeometry(LasPointGeometry point, Geometry geometry) {
    var envelope = geometry.getEnvelopeInternal();
    double minX = envelope.getMinX();
    double maxX = envelope.getMaxX();
    double minY = envelope.getMinY();
    double maxY = envelope.getMaxY();

    return point.getX() < minX || point.getX() > maxX || point.getY() < minY || point.getY() > maxY;
  }
}
