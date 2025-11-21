package app.bpartners.geojobs.service.lidar;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.service.lidar.api.LidarApi;
import app.bpartners.geojobs.utils.lidar.LidarRoofsAnalysisProcessorCreator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

@Slf4j
class LidarRoofsAnalysisProcessorTest {
  private static final LidarRoofsAnalysisProcessorCreator processorCreator =
      new LidarRoofsAnalysisProcessorCreator();

  @Test
  void compute_roof_slope_and_height_with_multiple_roof_geometries() {
    var roofGeometry1 = roofGeometry1();
    var roofGeometry2 = roofGeometry2();
    var roofGeometry3 = roofGeometry3();
    var roofGeometry4 = roofGeometry4();
    var roofGeometries = Set.of(roofGeometry1, roofGeometry2, roofGeometry3, roofGeometry4);

    var subject = processorCreator.create(roofGeometries);

    var roofsAnalysisResult = subject.from(roofGeometries);

    var expectedSet =
        Set.of(
            new Expected(roofGeometry1, 2d, 9.85, 3487, 2187),
            new Expected(roofGeometry2, 2d, 21.81, 4949, 1265),
            new Expected(roofGeometry3, 18d, 17.08, 3073, 1377),
            new Expected(roofGeometry4, 19d, 17.33, 5769, 2239));

    for (var geometry : roofGeometries) {
      var actual = roofsAnalysisResult.getProperties(geometry);
      var expected = getExpected(expectedSet, geometry);

      assertEquals(AVAILABLE, actual.getData().status());
      assertEquals(expected.roofPts(), actual.getData().roof().points().size());
      assertEquals(expected.groundPts(), actual.getData().ground().points().size());
      assertEquals(expected.height(), actual.getHeightInMeters().getValue(), 0.3);

      var firstPlane = actual.getPlanes().getFirst();
      assertEquals(expected.slope(), firstPlane.getSlopeInDegrees().getValue(), 10);
    }
  }

  @Test
  void status_should_be_extraction_error_when_unchecked_exception_happens() {
    var geometry1 = roofGeometry1();
    var lidarApiMock = mock(LidarApi.class);

    when(lidarApiMock.getUniqueLidarFilesUrls(any())).thenThrow();

    var subject = processorCreator.create(lidarApiMock);

    var roofsAnalysisResult = subject.from(Set.of(geometry1));

    var property = roofsAnalysisResult.getProperties(geometry1);

    assertEquals(EXTRACTION_ERROR, property.getData().status());
    assertEquals(0, property.getHeightInMeters().getValue());
    assertTrue(property.getPlanes().isEmpty());
  }

  @Test
  void status_should_be_unavailable_when_no_lidar_was_found() {
    var geometry1 = roofGeometry1();
    var lidarApiMock = mock(LidarApi.class);

    when(lidarApiMock.getUniqueLidarFilesUrls(any())).thenReturn(Map.of("url", Set.of(geometry1)));
    when(lidarApiMock.download(any())).thenReturn(Optional.empty());

    var subject = processorCreator.create(lidarApiMock);

    var roofsAnalysisResult = subject.from(Set.of(geometry1));

    var property = roofsAnalysisResult.getProperties(geometry1);

    assertEquals(UNAVAILABLE, property.getData().status());
    assertEquals(0, property.getHeightInMeters().getValue());
    assertTrue(property.getPlanes().isEmpty());
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

  private static Geometry roofGeometry2() {
    var roof2Coordinates =
        new Coordinate[] {
          new Coordinate(2.2431823989819577, 48.82457400501346),
          new Coordinate(2.243242034747283, 48.82446145324346),
          new Coordinate(2.24349250495996, 48.824520346643),
          new Coordinate(2.243502444253778, 48.8244941718074),
          new Coordinate(2.243595873618915, 48.824520346643),
          new Coordinate(2.2435342499950366, 48.82464598566398),
          new Coordinate(2.2431823989819577, 48.82457400501346)
        };
    return geometryFactory.createPolygon(roof2Coordinates);
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

  private static Geometry roofGeometry4() {
    var roof4Coordinates =
        new Coordinate[] {
          new Coordinate(2.2440344995304145, 48.825080630434144),
          new Coordinate(2.2441714180016277, 48.82492974092489),
          new Coordinate(2.244239877236623, 48.8249382325543),
          new Coordinate(2.2443420700077468, 48.825130273636546),
          new Coordinate(2.244264681307385, 48.825147910025805),
          new Coordinate(2.2442349164219877, 48.82510153284133),
          new Coordinate(2.2442111045139086, 48.82510153284133),
          new Coordinate(2.2442051515377557, 48.82507409843032),
          new Coordinate(2.2442269791192473, 48.82507409843032),
          new Coordinate(2.2442130888404392, 48.825047970405706),
          new Coordinate(2.244204159375073, 48.825049930008134),
          new Coordinate(2.244128755000105, 48.825116556440804),
          new Coordinate(2.2440344995304145, 48.825080630434144)
        };
    return geometryFactory.createPolygon(roof4Coordinates);
  }

  private static Expected getExpected(Set<Expected> expectedSet, Geometry geometry) {
    return expectedSet.stream()
        .filter(
            expected ->
                expected.geometry().getEnvelopeInternal().equals(geometry.getEnvelopeInternal()))
        .findFirst()
        .orElseThrow();
  }

  private record Expected(
      Geometry geometry, Double slope, Double height, Integer roofPts, Integer groundPts) {}
}
