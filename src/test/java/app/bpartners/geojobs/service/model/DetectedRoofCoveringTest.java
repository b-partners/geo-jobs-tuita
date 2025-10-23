package app.bpartners.geojobs.service.model;

import static app.bpartners.geojobs.repository.model.detection.RoofCoveringType.ROOF_TUILES;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.service.event.DetectionRoofPropertiesRequestedService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class DetectedRoofCoveringTest {

  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @SneakyThrows
  @Test
  void deserialize_detected_roof_covering() {
    var actual =
        objectMapper.readValue(
            """
            {\"primary\":\"ROOF_TUILES\",\"secondary\":null}""",
            DetectionRoofPropertiesRequestedService.DetectedRoofCovering.class);

    assertEquals(
        new DetectionRoofPropertiesRequestedService.DetectedRoofCovering(ROOF_TUILES, null),
        actual);
  }
}
