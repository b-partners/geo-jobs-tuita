package app.bpartners.geojobs.service.lidar.model.geometry.planes;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.service.lidar.model.geometry.roof.RoofProperties;
import app.bpartners.geojobs.utils.lidar.LidarRoofsAnalysisProcessorCreator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

class Planes3DExtractorTest {
  private static final Planes3DExtractor subject = new Planes3DExtractor(0.25, 0.5, 200);
  private static final LidarRoofsAnalysisProcessorCreator processorCreator =
      new LidarRoofsAnalysisProcessorCreator();

  @Test
  void extract_one_plane_ok() {
    var roofGeometry1 = roofGeometry1();
    var roofGeometries = Set.of(roofGeometry1);
    var processor = processorCreator.create(roofGeometries);
    var processResult = processor.from(roofGeometries);

    var property = new RoofProperties(processResult.getData(roofGeometry1));
    var planes = subject.apply(property.getCleanedRoofPoints());

    assertEquals(1, planes.size());
  }

  @Test
  void extract_multiples_plane_ok() {
    var roofGeometry3 = roofGeometry3();
    var roofGeometries = Set.of(roofGeometry3);
    var processor = processorCreator.create(roofGeometries);
    var processResult = processor.from(roofGeometries);

    var properties = new RoofProperties(processResult.getData(roofGeometry3));
    var planes = subject.apply(properties.getCleanedRoofPoints());

    assertEquals(2, planes.size());
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

  private static Geometry roofGeometry3() {
    var roof3Coordinates =
        new Coordinate[] {
          new Coordinate(2.243822051637153, 48.82470351681039),
          new Coordinate(2.2438825948823933, 48.824583284573066),
          new Coordinate(2.2440969775185238, 48.824634906058066),
          new Coordinate(2.2440423893471007, 48.82475317786745),
          new Coordinate(2.243822051637153, 48.82470351681039)
        };
    return geometryFactory.createPolygon(roof3Coordinates);
  }
}
