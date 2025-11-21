package app.bpartners.geojobs.service.lidar.api;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.OK;

import app.bpartners.geojobs.conf.FacadeIT;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class LidarApiIT extends FacadeIT {
  @Autowired LidarApi subject;
  @MockBean RestTemplate restTemplate;

  private static final String LIDAR_FILE_URL = "https://storage.sbg.cloud.ovh.net/dummy.laz";

  @Test
  void get_lidar_laz_file_urls_ok() {
    when(restTemplate.getForEntity(any(String.class), eq(FeatureCollection.class)))
        .thenReturn(
            new ResponseEntity<>(
                FeatureCollection.builder()
                    .features(
                        List.of(
                            FeatureCollection.Feature.builder()
                                .properties(Map.of("url", LIDAR_FILE_URL))
                                .build()))
                    .build(),
                OK));

    var actual = subject.getUniqueLidarFilesUrls(Set.of(geometry1(), geometry2()));

    assertTrue(actual.containsKey(LIDAR_FILE_URL));
    assertEquals(1, actual.size());
    assertEquals(2, actual.get(LIDAR_FILE_URL).size());
  }

  private static Geometry geometry1() {
    var coordinates =
        new Coordinate[] {
          new Coordinate(3.243891733457616, 49.82448842864014),
          new Coordinate(2.243947393505863, 48.82437718542337),
          new Coordinate(2.244263463059525, 48.82456695311532),
          new Coordinate(3.243891733457616, 49.82448842864014)
        };
    return geometryFactory.createPolygon(coordinates);
  }

  private static Geometry geometry2() {
    var coordinates =
        new Coordinate[] {
          new Coordinate(2.243891733457616, 48.82448842864014),
          new Coordinate(2.243947393505863, 48.82437718542337),
          new Coordinate(2.244263463059525, 48.82456695311532),
          new Coordinate(2.243891733457616, 48.82448842864014)
        };
    return geometryFactory.createPolygon(coordinates);
  }

  @Test
  void download_should_return_correct_file() {
    when(restTemplate.getForObject(any(String.class), eq(byte[].class)))
        .thenReturn(new byte[] {1, 2, 3});

    var actual = subject.download(LIDAR_FILE_URL);

    assertTrue(actual.isPresent());
  }
}
