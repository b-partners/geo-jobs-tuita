package app.bpartners.geojobs.service.cityjson.model;

import app.bpartners.geojobs.service.lidar.model.geometry.GeometryWithProperties;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record BuildingData(
    String id,
    List<GeometryWithProperties> roofs,
    List<GeometryWithProperties> walls,
    List<GeometryWithProperties> grounds,
    Map<String, Object> properties) {}
