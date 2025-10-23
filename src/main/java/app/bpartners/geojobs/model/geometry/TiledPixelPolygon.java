package app.bpartners.geojobs.model.geometry;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import java.util.List;

public record TiledPixelPolygon(
    Feature feature, List<PolygonObjectType> polygons, int tileX, int tileY, int zoom) {}
