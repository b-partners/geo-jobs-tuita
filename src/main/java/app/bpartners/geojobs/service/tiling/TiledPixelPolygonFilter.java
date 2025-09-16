package app.bpartners.geojobs.service.tiling;

import app.bpartners.geojobs.model.geometry.TiledPixelPolygon;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TiledPixelPolygonFilter {
  private final TileProjection tileProjection;

  // TODO: check if TiledPixelPolygon does not contain not used shell
  public List<TiledPixelPolygon> filterPolygonsInMask(
      List<TiledPixelPolygon> pixelPolygons, Geometry maskGeoJson) {
    var preparedMask = new PreparedGeometryFactory().create(maskGeoJson);
    return pixelPolygons.stream()
        .map(
            tiledPixelPolygon -> {
              var filteredGeoPolygons =
                  tiledPixelPolygon.polygons().stream()
                      .filter(
                          pixelPolygon -> {
                            var geoPolygon =
                                tileProjection.pixelPolygonToGeo(
                                    pixelPolygon.polygon(),
                                    tiledPixelPolygon.tileX(),
                                    tiledPixelPolygon.tileY(),
                                    tiledPixelPolygon.zoom());
                            return preparedMask.intersects(geoPolygon);
                          })
                      .toList();
              return new TiledPixelPolygon(
                  tiledPixelPolygon.point(),
                  filteredGeoPolygons,
                  tiledPixelPolygon.tileX(),
                  tiledPixelPolygon.tileY(),
                  tiledPixelPolygon.zoom());
            })
        .toList();
  }
}
