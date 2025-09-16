package app.bpartners.geojobs.service.lidar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.OK;

import app.bpartners.geojobs.conf.FacadeIT;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class LidarApiIT extends FacadeIT {
  @Autowired LidarApi subject;
  @MockBean RestTemplate restTemplate;

  @Test
  void get_lidar_laz_file_urls_ok() {
    when(restTemplate.getForEntity(any(String.class), eq(FeatureCollection.class)))
        .thenReturn(
            new ResponseEntity<>(
                FeatureCollection.builder()
                    .features(
                        List.of(
                            FeatureCollection.Feature.builder()
                                .properties(
                                    Map.of("url", "https://storage.sbg.cloud.ovh.net/dummy.laz"))
                                .build()))
                    .build(),
                OK));
    when(restTemplate.getForEntity(any(String.class), eq(File.class)))
        .thenReturn(new ResponseEntity<>(mock(File.class), OK));

    when(restTemplate.getForObject(any(String.class), eq(byte[].class)))
        .thenReturn(new byte[] {1, 2, 3});

    var randomBbox = new Envelope(635142.88, 635289.49, 6859875.04, 6859993.81);

    var actual = subject.apply(Set.of(randomBbox));

    assertEquals(1, actual.size());
  }
}
