package app.bpartners.geojobs.service.lidar;

import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;
import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.*;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.lidar.api.LidarApi;
import app.bpartners.geojobs.service.lidar.model.*;
import app.bpartners.geojobs.service.lidar.model.roof.LidarRoofData;
import app.bpartners.geojobs.service.lidar.model.roof.RoofProperties;
import com.github.mreutegg.laszip4j.LASReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LidarRoofsAnalysisProcessor
    implements Function<Set<Geometry>, LidarRoofsAnalysisProcessor.RoofsAnalysisResult> {
  private final LidarApi lidarApi;
  private final GeometrySquareMeterArea projector;

  private static final int ROOF_GROUND_BUFFER_METERS = 3;
  private static final short ROOF_LIDAR_CLASS_VALUE = 6;
  private static final short GROUND_LIDAR_CLASS_VALUE = 2;

  @Override
  public RoofsAnalysisResult apply(Set<Geometry> roofsEPSG4326) {
    Set<LidarRoofData> allRoofsData = emptyFromEPSG4326(roofsEPSG4326);

    try {
      Map<String, Set<Geometry>> lidarFilesUrl =
          lidarApi.getUniqueLidarFilesUrls(
              allRoofsData.stream().map(data -> data.roof().boundaryLambert93()).collect(toSet()));
      List<Set<LidarRoofData>> roofsDataPerFiles =
          lidarFilesUrl.entrySet().parallelStream()
              .map(
                  entry ->
                      getRoofsDataFromFileUrl(
                          entry.getKey(), getRoofsDataToProcess(allRoofsData, entry.getValue())))
              .toList();

      return new RoofsAnalysisResult(mergeSameRoofBoundary(roofsDataPerFiles));
    } catch (Exception e) {
      log.error(e.getMessage());
      return new RoofsAnalysisResult(
          mergeSameRoofBoundary(List.of(emptyFrom(allRoofsData, EXTRACTION_ERROR))));
    }
  }

  private static Map<String, LidarRoofData> mergeSameRoofBoundary(
      List<Set<LidarRoofData>> roofsDataFromFiles) {
    Map<String, LidarRoofData> merged = new HashMap<>();
    for (var roofDataFromOneFile : roofsDataFromFiles) {
      for (var data : roofDataFromOneFile) {
        var key = RoofsAnalysisResult.geometryKey(data.roof().boundaryEPSG4326());
        merged.merge(key, data, LidarRoofData::merge);
      }
    }
    return merged;
  }

  private Set<LidarRoofData> getRoofsDataToProcess(
      Set<LidarRoofData> allRoofsData, Set<Geometry> roofsLambert93ToProcess) {
    return allRoofsData.stream()
        .filter(
            data ->
                roofsLambert93ToProcess.stream()
                    .anyMatch(
                        g ->
                            g.getEnvelopeInternal()
                                .equals(data.roof().boundaryLambert93().getEnvelopeInternal())))
        .collect(toSet());
  }

  private Set<LidarRoofData> getRoofsDataFromFileUrl(String fileUrl, Set<LidarRoofData> roofsData) {
    File file;
    try {
      var optionalFile = lidarApi.download(fileUrl);
      if (optionalFile.isEmpty()) {
        return emptyFrom(roofsData, UNAVAILABLE);
      }
      file = optionalFile.get();
    } catch (Exception e) {
      return emptyFrom(roofsData, EXTRACTION_ERROR);
    }

    var roofsDataFromFile = emptyFrom(roofsData, AVAILABLE);

    var lasReader = new LASReader(file);
    var lasHeader = lasReader.getHeader();
    log.info("Reading lasPoints from file url: {}", file.getPath());
    for (var point : lasReader.getPoints()) {
      var pointClassification = point.getClassification();

      switch (pointClassification) {
        case GROUND_LIDAR_CLASS_VALUE:
          var groundPoint = new LasPointGeometry(point, lasHeader);
          handleGroundPoint(groundPoint, roofsDataFromFile);
          break;
        case ROOF_LIDAR_CLASS_VALUE:
          var roofPoint = new LasPointGeometry(point, lasHeader);
          handleRoofPoint(roofPoint, roofsDataFromFile);
          break;
        default:
          break;
      }
    }

    log.info("Finished reading lasPoints from: {}", file.getPath());
    remove(file);
    return roofsDataFromFile;
  }

  private static void handleGroundPoint(
      LasPointGeometry groundPoint, Set<LidarRoofData> roofsData) {
    for (var roofData : roofsData) {
      var groundLambert93Geometry = roofData.ground().boundaryLambert93();

      if (!groundLambert93Geometry
          .getEnvelopeInternal()
          .contains(groundPoint.getX(), groundPoint.getY())) {
        continue;
      }

      if (groundLambert93Geometry.contains(groundPoint)) {
        roofData.ground().points().add(groundPoint);
        break;
      }
    }
  }

  private static void handleRoofPoint(LasPointGeometry roofPoint, Set<LidarRoofData> roofsData) {
    for (var roofData : roofsData) {
      var roofLambert93Geometry = roofData.roof().boundaryLambert93();

      if (!roofLambert93Geometry
          .getEnvelopeInternal()
          .contains(roofPoint.getX(), roofPoint.getY())) {
        continue;
      }

      if (roofLambert93Geometry.contains(roofPoint)) {
        roofData.roof().points().add(roofPoint);
        break;
      }
    }
  }

  private Set<LidarRoofData> emptyFromEPSG4326(Set<Geometry> roofsEPSG4326) {
    Set<LidarRoofData> lidarData = new HashSet<>();
    for (var roofEPSG4326 : roofsEPSG4326) {
      var roofLambert93 = projector.project(roofEPSG4326, WGS84, LAMBERT_93);
      var groundLambert93 = roofLambert93.buffer(ROOF_GROUND_BUFFER_METERS);

      lidarData.add(
          LidarRoofData.empty(roofEPSG4326, roofLambert93, null, groundLambert93, UNAVAILABLE));
    }
    return lidarData;
  }

  private static Set<LidarRoofData> emptyFrom(
      Set<LidarRoofData> roofsData, LidarDataStatus status) {
    return roofsData.stream()
        .map(
            data ->
                LidarRoofData.empty(
                    data.roof().boundaryEPSG4326(),
                    data.roof().boundaryLambert93(),
                    data.ground().boundaryEPSG4326(),
                    data.ground().boundaryLambert93(),
                    status))
        .collect(toSet());
  }

  private static void remove(File file) {
    try {
      Files.deleteIfExists(file.toPath());
    } catch (IOException e) {
      log.warn("Failed to delete file {}", file.getPath(), e);
    }
  }

  public record RoofsAnalysisResult(Map<String, LidarRoofData> roofsData) {
    public RoofProperties getProperties(Geometry roofEPSG4326) {
      return new RoofProperties(getData(roofEPSG4326));
    }

    public LidarRoofData getData(Geometry roofEPSG4326) {
      return roofsData.getOrDefault(
          geometryKey(roofEPSG4326),
          LidarRoofData.empty(roofEPSG4326, null, null, null, UNAVAILABLE));
    }

    private static String geometryKey(Geometry geometry) {
      var envelope = geometry.getEnvelopeInternal();
      return envelope.getMinX()
          + "_"
          + envelope.getMaxX()
          + "_"
          + envelope.getMinY()
          + "_"
          + envelope.getMaxY();
    }
  }
}
