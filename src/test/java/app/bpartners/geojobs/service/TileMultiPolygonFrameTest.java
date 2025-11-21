package app.bpartners.geojobs.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class TileMultiPolygonFrameTest {

  GeometryConverter geometryConverter = new GeometryConverter(null);
  TileMultiPolygonFrame subject = new TileMultiPolygonFrame(new TileFinder(), geometryConverter);

  @Test
  void retrieve_multipolygon_tile() {
    var actual =
        subject
            .apply(BigDecimal.valueOf(-0.24917235102128643), BigDecimal.valueOf(46.65192666192337))
            .orElseThrow();

    assertNotNull(actual);
    log.info("{}", geometryConverter.writeGeometryAsString(actual));
  }
}
