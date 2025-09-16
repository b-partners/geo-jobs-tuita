package app.bpartners.geojobs.service.lidar;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.file.FileWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Slf4j
class LidarPolygonMetricProcessorIT extends FacadeIT {
  @Autowired LidarPolygonMetricProcessor subject;
  @MockBean LidarApi lidarApiMock;
  @Autowired FileWriter fileWriter;
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
    when(lidarApiMock.apply(any(List.class))).thenReturn(Set.of(lasFile));

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
    var roofGeometry1 = geometryFactory.createPolygon(roof1Coordinates);

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
    var roofGeometry2 = geometryFactory.createPolygon(roof2Coordinates);

    var roof3Coordinates =
        new Coordinate[] {
          new Coordinate(2.243822051637153, 48.82470351681039),
          new Coordinate(2.2438825948823933, 48.824583284573066),
          new Coordinate(2.2440969775185238, 48.824634906058066),
          new Coordinate(2.2440423893471007, 48.82475317786745),
          new Coordinate(2.243822051637153, 48.82470351681039)
        };
    var roofGeometry3 = geometryFactory.createPolygon(roof3Coordinates);

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
    var roofGeometry4 = geometryFactory.createPolygon(roof4Coordinates);

    var dimensions =
        subject.apply(List.of(roofGeometry1, roofGeometry2, roofGeometry3, roofGeometry4));
    assertEquals(4, dimensions.size());

    double[] expectedSlopes = {3, 23.07, 44.23, 42.74};
    double[] expectedHeights = {9.85, 21.81, 17.08, 17.33};
    int[] expectedRoofPts = {3225, 3846, 2923, 5074};
    int[] expectedSolPts = {2187, 1265, 1377, 2239};
    for (int i = 0; i < dimensions.size(); i++) {
      var dimension = dimensions.get(i);

      assertEquals(expectedSlopes[i], dimension.getSlopeInDegrees(), 3);
      assertEquals(expectedHeights[i], dimension.getHeightInMeters(), 0.3);

      assertEquals(expectedRoofPts[i], dimension.roof().points().size());
      assertEquals(expectedSolPts[i], dimension.sol().points().size());
    }
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
}
