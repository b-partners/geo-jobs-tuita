package app.bpartners.geojobs.service.cityjson.factory;

import static app.bpartners.geojobs.service.cityjson.model.ConstructionSurfaceType.*;

import app.bpartners.geojobs.service.cityjson.model.BuildingData;
import app.bpartners.geojobs.service.cityjson.model.ConstructionSurfaceType;
import app.bpartners.geojobs.service.lidar.model.geometry.GeometryWithProperties;
import java.util.List;
import org.citygml4j.core.model.building.Building;
import org.citygml4j.core.model.core.AbstractSpaceBoundaryProperty;

public class BuildingFactory {
  private BuildingFactory() {}

  public static Building make(BuildingData roofData) {
    var building = new Building();
    building.setId(roofData.id());

    var roofBoundaries = toAbstractSpaceBoundaryProperty(ROOF, roofData.roofs());
    roofBoundaries.forEach(building::addBoundary);

    var wallBoundaries = toAbstractSpaceBoundaryProperty(WALL, roofData.walls());
    wallBoundaries.forEach(building::addBoundary);

    var groundBoundaries = toAbstractSpaceBoundaryProperty(GROUND, roofData.grounds());
    groundBoundaries.forEach(building::addBoundary);

    var properties = GenericAttributeFactory.make(roofData.properties());
    building.setGenericAttributes(properties);

    return building;
  }

  public static List<AbstractSpaceBoundaryProperty> toAbstractSpaceBoundaryProperty(
      ConstructionSurfaceType type, List<GeometryWithProperties> polygonsWithProperties) {
    return polygonsWithProperties.stream()
        .map(polygonWithProperties -> ConstructionSurfaceFactory.make(type, polygonWithProperties))
        .map(AbstractSpaceBoundaryProperty::new)
        .toList();
  }
}
