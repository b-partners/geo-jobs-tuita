package app.bpartners.geojobs.service;

import static org.geotools.geometry.jts.JTS.transform;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

@Component
public class PolygonInsideCircleDistanceComputer
    implements BiFunction<List<BigDecimal>, List<List<BigDecimal>>, Double> {
  private static final GeometryFactory GF = new GeometryFactory();
  private static final CoordinateReferenceSystem WGS84;
  private static final CoordinateReferenceSystem LAMBERT_93;

  static {
    try {
      WGS84 = CRS.decode("EPSG:4326", true);
      LAMBERT_93 = CRS.decode("EPSG:2154", true);
    } catch (Exception e) {
      throw new RuntimeException("Erreur chargement CRS", e);
    }
  }

  /** Transforme des coordonnées WGS84 en Lambert-93 */
  private static Geometry project(Geometry geom) {
    try {
      MathTransform transform =
          CRS.findMathTransform(
              PolygonInsideCircleDistanceComputer.WGS84,
              PolygonInsideCircleDistanceComputer.LAMBERT_93,
              true);
      return transform(geom, transform);
    } catch (Exception e) {
      throw new RuntimeException("Erreur projection", e);
    }
  }

  /** Vérifie si le polygone est inscrit dans un cercle de 1000 m autour du centre (lon, lat). */
  @Override
  public Double apply(
      List<BigDecimal> circleCenterCoordinates, List<List<BigDecimal>> polygonLonLat) {

    // 1) Centre WGS84 → Lambert-93
    var longitude = circleCenterCoordinates.getFirst().doubleValue();
    var latitude = circleCenterCoordinates.getLast().doubleValue();
    var centerWGS84 = GF.createPoint(new Coordinate(longitude, latitude));
    var centerL93 = (Point) project(centerWGS84);

    // 2) Construire le polygone WGS84
    Coordinate[] coords = new Coordinate[polygonLonLat.size() + 1];

    for (int i = 0; i < polygonLonLat.size(); i++) {
      List<BigDecimal> p = polygonLonLat.get(i);
      coords[i] =
          new Coordinate(
              p.get(0).doubleValue(), // lon
              p.get(1).doubleValue() // lat
              );
    }
    coords[coords.length - 1] = coords[0];

    Polygon polygonWGS84 = GF.createPolygon(coords);

    // 3) Transformer en Lambert-93
    Polygon polygonL93 = (Polygon) project(polygonWGS84);

    // 4) Calculer le rayon minimal (distance max centre → vertex)
    double max = 0;

    for (Coordinate c : polygonL93.getCoordinates()) {
      double dx = c.x - centerL93.getX();
      double dy = c.y - centerL93.getY();
      double dist = Math.sqrt(dx * dx + dy * dy);
      max = Math.max(max, dist);
    }

    return max; // rayon en mètres
  }
}
