package app.bpartners.geojobs.conf;

import org.springframework.test.context.DynamicPropertyRegistry;

public class EnvConf {

  public static final String ANNOTATOR_USER_ID_FOR_GEOJOBS = "geo-jobs_user_id";
  public static final String ADMIN_EMAIL = "admin@gmail.com";
  public static final String ADMIN_API_KEY = "the-admin-api-key";
  public static final String LIDAR_API_URL =
      "https://data.geopf.fr/private/wfs/?apikey=interface_catalogue";

  void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("annotator.api.url", () -> "http://dummy.com");
    registry.add("tiles.downloader.mock.activated", () -> "false");
    registry.add("objects.detector.mock.activated", () -> "false");
    registry.add(
        "tiles.downloader.api.url",
        () -> "https://qbzvaia3tcgkigveg3jh6zruge0kbjld.lambda-url.eu-west-3.on.aws");
    registry.add(
        "tile.detection.api.url",
        () -> "https://jgzoqzwhm6r3oxcovqidd5onvm0sipnc.lambda-url.eu-west-3.on.aws/");
    registry.add("admin.api.key", () -> ADMIN_API_KEY);
    registry.add("annotator.api.key", () -> ADMIN_API_KEY);
    registry.add(
        "annotator.geojobs.user.info",
        () ->
            "{\"userId\":\""
                + ANNOTATOR_USER_ID_FOR_GEOJOBS
                + "\", \"teamId\":\"geo_jobs_team_id\"}");
    registry.add("jobs.status.update.retry.max.attempt", () -> 0);
    registry.add("admin.email", () -> ADMIN_EMAIL);
    registry.add("readme.monitor.url", () -> "https://dummy.com");
    registry.add("readme.monitor.api-key", () -> "the-readme-monitor-api-key");
    registry.add("readme.monitor.development", () -> "true");
    registry.add("readme.webhook.secret", () -> "the-readme-webhook-secret");
    registry.add("bpartners.api.url", () -> "http://dummy.com");
    registry.add("geoserver.api.url", () -> "http://dummy-geoserver.com");
    registry.add(
        "roof.covering.detection.api.url",
        () -> "https://dyp5ye459j.execute-api.eu-west-3.amazonaws.com/Prod");
    registry.add("ign.lidar.api.url", () -> LIDAR_API_URL);
  }
}
