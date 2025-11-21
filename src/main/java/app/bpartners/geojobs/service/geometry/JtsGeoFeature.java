package app.bpartners.geojobs.service.geometry;

import java.util.Map;
import org.locationtech.jts.geom.Geometry;

public record JtsGeoFeature(Map<String, Object> properties, Geometry geometry) {}
