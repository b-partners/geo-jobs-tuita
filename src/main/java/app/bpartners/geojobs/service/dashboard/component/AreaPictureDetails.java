package app.bpartners.geojobs.service.dashboard.component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AreaPictureDetails(
    String id,
    AreaPictureMapLayer actualLayer,
    GeoPosition currentGeoPosition,
    TileCoordinates referenceTile) {}
