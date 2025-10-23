package app.bpartners.geojobs.service.lidar.api;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@AllArgsConstructor
@Slf4j
public class LidarApi {
  private final LidarApiConf conf;
  private final RestTemplate restTemplate;
  private static final Set<String> ALLOWED_URL_PREFIXES =
      Set.of(
          "https://storage.sbg.cloud.ovh.net/",
          "https://lidar.data.gouv.fr/",
          "https://data.geopf.fr/");

  public Map<String, Set<Geometry>> getUniqueLidarFilesUrls(Collection<Geometry> geometries) {
    Map<String, Set<Geometry>> filesUrls = new HashMap<>();

    for (var geometry : geometries) {
      var uriBuilder = UriComponentsBuilder.fromHttpUrl(conf.getUrl());
      conf.getDefaultParams(geometry.getEnvelopeInternal()).forEach(uriBuilder::queryParam);

      var features =
          requireNonNull(
                  restTemplate
                      .getForEntity(uriBuilder.toUriString(), FeatureCollection.class)
                      .getBody())
              .getFeatures();

      for (var feature : features) {
        var url = feature.getProperties().get("url").toString();
        log.info("LAZ File to download: {}", url);
        filesUrls.computeIfAbsent(url, key -> new HashSet<>()).add(geometry);
      }
    }

    return filesUrls;
  }

  public Optional<File> download(String fileUrl) {
    if (!isSafeUrl(fileUrl)) {
      log.warn("Unsafe URL blocked: {}", fileUrl);
      return Optional.empty();
    }

    try {
      byte[] data = restTemplate.getForObject(fileUrl, byte[].class);
      if (data == null) {
        return Optional.empty();
      }

      var tempFile = File.createTempFile("lidar-", ".laz");
      try (var outputStream = new FileOutputStream(tempFile)) {
        outputStream.write(data);
      }

      return Optional.of(tempFile);
    } catch (Exception e) {
      log.error("Failed to download LAZ file from {}", fileUrl, e);
      throw new RuntimeException("Could not download file: " + fileUrl, e);
    }
  }

  public static boolean isSafeUrl(String url) {
    if (url == null) return false;
    return ALLOWED_URL_PREFIXES.stream().anyMatch(url::startsWith);
  }
}
