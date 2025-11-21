package app.bpartners.geojobs.service.cityjson;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.bpartners.geojobs.service.cityjson.factory.CityJsonFactory;
import app.bpartners.geojobs.utils.lidar.LidarRoofsAnalysisProcessorCreator;
import java.nio.file.Files;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

class LidarDataToCityJsonProcessorTest {
  private static final LidarDataToCityJsonProcessor subject =
      new LidarDataToCityJsonProcessor(new CityJsonFactory());

  private static final LidarRoofsAnalysisProcessorCreator processorCreator =
      new LidarRoofsAnalysisProcessorCreator();

  @Test
  void generate_cityjson_ok() {
    var roofsGeometries = Set.of(roofGeometry1());
    var processor = processorCreator.create(roofsGeometries);

    var id = randomUUID().toString();
    var analysisResult = processor.from(roofsGeometries);
    var actual = subject.apply(id, analysisResult);

    assertNotNull(actual);
    assertTrue(Files.exists(actual.toPath()));
    assertTrue(actual.getPath().endsWith(id + ".json"));
  }

  private static Geometry roofGeometry1() {
    var roof1Coordinates =
        new Coordinate[] {
          new Coordinate(2.243891733457616, 48.82448842864014),
          new Coordinate(2.243947393505863, 48.82437718542337),
          new Coordinate(2.244038835011281, 48.82440597780899),
          new Coordinate(2.2440209442821413, 48.82445309258651),
          new Coordinate(2.244197863717403, 48.8244975898354),
          new Coordinate(2.24422768160008, 48.82447010624497),
          new Coordinate(2.24432906240051, 48.824487119898066),
          new Coordinate(2.244263463059525, 48.82456695311532),
          new Coordinate(2.243891733457616, 48.82448842864014)
        };
    return geometryFactory.createPolygon(roof1Coordinates);
  }
}
