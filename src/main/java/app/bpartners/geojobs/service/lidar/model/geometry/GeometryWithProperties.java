package app.bpartners.geojobs.service.lidar.model.geometry;

import java.io.Serializable;
import java.util.Map;
import lombok.Builder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

@Builder
public record GeometryWithProperties(Geometry geometry, Map<String, Object> properties)
    implements Serializable {

  public Polygon asPolygon() {
    return switch (geometry) {
      case Polygon polygon -> polygon;
      case MultiPolygon multiPolygon -> {
        if (multiPolygon.getNumGeometries() == 0) {
          throw new IllegalArgumentException("Unsupported Geometry Type");
        }

        yield (Polygon) multiPolygon.getGeometryN(0);
      }
      default -> throw new IllegalArgumentException("Unsupported Geometry Type");
    };
  }
}
