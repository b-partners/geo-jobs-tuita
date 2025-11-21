package app.bpartners.geojobs.utils.lidar;

import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.file.ExtensionGuesser;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor;
import app.bpartners.geojobs.service.lidar.api.LidarApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.locationtech.jts.geom.Geometry;

public class LidarRoofsAnalysisProcessorCreator {
  private static final GeometrySquareMeterArea projector = new GeometrySquareMeterArea();
  private static final FileWriter fileWriter =
      new FileWriter(new ObjectMapper(), new ExtensionGuesser());
  public static final String LARGE_LIDAR_FILE_PATH =
      "las/LHD_FXX_0644_6859_PTS_O_LAMB93_IGN69.copc.laz";

  public LidarRoofsAnalysisProcessor create(Set<Geometry> geometries) {
    var projected =
        geometries.stream().map(g -> projector.project(g, WGS84, LAMBERT_93)).collect(toSet());
    var lidarApiMock = lidarApiMock(projected, createTempFileFromResources(LARGE_LIDAR_FILE_PATH));

    return new LidarRoofsAnalysisProcessor(lidarApiMock, projector);
  }

  public LidarRoofsAnalysisProcessor create(LidarApi lidarApi) {
    return new LidarRoofsAnalysisProcessor(lidarApi, projector);
  }

  @SneakyThrows
  public File createTempFileFromResources(String path) {
    var lasFileFromResource =
        new File(requireNonNull(getClass().getClassLoader().getResource(path)).getFile());
    return fileWriter.write(
        Files.readAllBytes(lasFileFromResource.toPath()),
        FileWriter.createTempDirectory(),
        lasFileFromResource.getName());
  }

  private static LidarApi lidarApiMock(Set<Geometry> lambert93Geometries, File file) {
    LidarApi lidarApiMock = mock();

    when(lidarApiMock.getUniqueLidarFilesUrls(any()))
        .thenReturn(Map.of("url", lambert93Geometries));
    when(lidarApiMock.download(any())).thenReturn(Optional.of(file));

    return lidarApiMock;
  }
}
