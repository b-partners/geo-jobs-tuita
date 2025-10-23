package app.bpartners.geojobs.service.lidar;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;
import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.AVAILABLE;
import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.EXTRACTION_ERROR;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.lidar.api.LidarApi;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Slf4j
class LidarRoofsAnalysisProcessorIT extends FacadeIT {
  @Autowired LidarRoofsAnalysisProcessor subject;
  @MockBean LidarApi lidarApiMock;
  @Autowired FileWriter fileWriter;
  @Autowired GeometrySquareMeterArea projector;

  File lasFile;

  @BeforeEach
  void setUp() {
    lasFile = createLidarTestTempFile();
  }

  @AfterEach
  @SneakyThrows
  void cleanUp() {
    Files.deleteIfExists(lasFile.toPath());
  }

  @Test
  void compute_roof_slope_and_height_with_multiple_roof_geometries() {
    var roofGeometry1 = roofGeometry1();
    var roofGeometry2 = roofGeometry2();
    var roofGeometry3 = roofGeometry3();
    var roofGeometry4 = roofGeometry4();
    var roofGeometries = Set.of(roofGeometry1, roofGeometry2, roofGeometry3, roofGeometry4);
    var projected =
        roofGeometries.stream().map(g -> projector.project(g, WGS84, LAMBERT_93)).collect(toSet());

    when(lidarApiMock.getUniqueLidarFilesUrls(projected)).thenReturn(Map.of("url", projected));
    when(lidarApiMock.download(any())).thenReturn(Optional.of(lasFile));

    var roofsAnalysisResult = subject.apply(roofGeometries);

    var expectedSet =
        Set.of(
            new Expected(roofGeometry1, 1.53, 9.85, 3225, 2187),
            new Expected(roofGeometry2, 5.21, 21.81, 3846, 1265),
            new Expected(roofGeometry3, 44.23, 17.08, 2923, 1377),
            new Expected(roofGeometry4, 42.74, 17.33, 5074, 2239));

    for (var geometry : roofGeometries) {
      var actual = roofsAnalysisResult.getProperties(geometry);
      var expected = getExpected(expectedSet, geometry);

      assertEquals(AVAILABLE, actual.getData().status());
      assertEquals(expected.roofPts(), actual.getData().roof().points().size());
      assertEquals(expected.groundPts(), actual.getData().ground().points().size());
      assertEquals(expected.slope(), actual.getSlopeInDegree(), 3);
      assertEquals(expected.height(), actual.getHeightInMeter(), 0.3);
    }
  }

  @Test
  void status_should_be_runtime_when_unchecked_exception_happens() {
    var geometry1 = roofGeometry1();

    when(lidarApiMock.getUniqueLidarFilesUrls(anySet())).thenThrow(new RuntimeException());

    var roofsAnalysisResult = subject.apply(Set.of(geometry1));

    var property = roofsAnalysisResult.getProperties(geometry1);

    assertEquals(EXTRACTION_ERROR, property.getData().status());
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

  @SneakyThrows
  private File createLidarTestTempFile() {
    var lasFileFromResource =
        new File(
            requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("las/LHD_FXX_0644_6859_PTS_O_LAMB93_IGN69.copc.laz"))
                .getFile());

    return fileWriter.write(
        Files.readAllBytes(lasFileFromResource.toPath()),
        FileWriter.createTempDirectory(),
        lasFileFromResource.getName());
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
