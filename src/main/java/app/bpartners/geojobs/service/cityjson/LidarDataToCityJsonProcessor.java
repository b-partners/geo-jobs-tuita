package app.bpartners.geojobs.service.cityjson;

import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.AVAILABLE;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.service.cityjson.exception.CityJsonException;
import app.bpartners.geojobs.service.cityjson.factory.BuildingGroundPolygonFactory;
import app.bpartners.geojobs.service.cityjson.factory.BuildingWallPolygonFactory;
import app.bpartners.geojobs.service.cityjson.factory.CityJsonFactory;
import app.bpartners.geojobs.service.cityjson.model.BuildingData;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor;
import app.bpartners.geojobs.service.lidar.model.geometry.GeometryWithProperties;
import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import app.bpartners.geojobs.service.lidar.model.geometry.roof.LidarRoofData;
import app.bpartners.geojobs.service.lidar.model.geometry.roof.RoofPlane3D;
import app.bpartners.geojobs.service.lidar.model.geometry.roof.RoofProperties;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LidarDataToCityJsonProcessor
    implements BiFunction<String, LidarRoofsAnalysisProcessor.RoofsAnalysisResult, File> {

  private final CityJsonFactory cityJsonFactory;
  private static final String PLANE_SLOPE_KEY = "slope_in_degrees";
  private static final String ID_KEY = "id";

  @Override
  public File apply(
      String id, LidarRoofsAnalysisProcessor.RoofsAnalysisResult roofsAnalysisResults) {
    var buildingsData =
        roofsAnalysisResults.roofsData().values().stream()
            .filter(data -> AVAILABLE.equals(data.status()))
            .map(LidarDataToCityJsonProcessor::toBuildingData)
            .toList();

    try {
      var cityJsonFile = cityJsonFactory.make(id, id, buildingsData);
      log.info("CityJSON file saved at {}", cityJsonFile.getAbsolutePath());
      return cityJsonFile;
    } catch (CityJsonException e) {
      throw new RuntimeException(e);
    }
  }

  private static BuildingData toBuildingData(LidarRoofData lidarRoofData) {
    var roofProperty = new RoofProperties(lidarRoofData);
    var planes = roofProperty.getPlanes();
    var groundZ =
        roofProperty.getCleanedGroundPoints().stream()
            .mapToDouble(LasPointGeometry::getZ)
            .average()
            .orElse(0);

    var roofs = planes.stream().map(LidarDataToCityJsonProcessor::toPolygonWithProperties).toList();

    var walls =
        planes.stream().map(plane -> createWalls(plane, groundZ)).toList().stream()
            .flatMap(List::stream)
            .toList();

    var grounds = planes.stream().map(plane -> createGround(plane, groundZ)).toList();

    var properties = getProperties(lidarRoofData);
    var id = properties.getOrDefault(ID_KEY, randomUUID().toString());

    return BuildingData.builder()
        .id(String.valueOf(id))
        .walls(walls)
        .roofs(roofs)
        .grounds(grounds)
        .properties(properties)
        .build();
  }

  private static Map<String, Object> getProperties(LidarRoofData data) {
    return data.properties() == null ? new HashMap<>() : data.properties();
  }

  private static GeometryWithProperties toPolygonWithProperties(RoofPlane3D plane) {
    var slope = plane.getSlopeInDegrees().getValue();
    return GeometryWithProperties.builder()
        .geometry(plane.getDelimitation())
        .properties(Map.of(PLANE_SLOPE_KEY, slope))
        .build();
  }

  private static List<GeometryWithProperties> createWalls(RoofPlane3D plane, double groundZ) {
    var roofPolygon = plane.getDelimitation();
    return BuildingWallPolygonFactory.make(roofPolygon, groundZ);
  }

  private static GeometryWithProperties createGround(RoofPlane3D plane, double groundZ) {
    var roofPolygon = plane.getDelimitation();
    var groundPolygon = BuildingGroundPolygonFactory.make(roofPolygon, groundZ);
    return new GeometryWithProperties(groundPolygon, Map.of());
  }
}
