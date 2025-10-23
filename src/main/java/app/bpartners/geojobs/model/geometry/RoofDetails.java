package app.bpartners.geojobs.model.geometry;

import java.util.List;
import org.locationtech.jts.geom.MultiPolygon;

public record RoofDetails(MultiPolygon latLonGeometry, List<String> addresses) {}
