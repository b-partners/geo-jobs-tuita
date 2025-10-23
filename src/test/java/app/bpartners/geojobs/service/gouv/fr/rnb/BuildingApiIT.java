package app.bpartners.geojobs.service.gouv.fr.rnb;

import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("TODO: UnknownHostException from https://rnb-api.beta.gouv.fr in GitHub CI")
@Slf4j
class BuildingApiIT {
  BuildingApi subject = new BuildingApi();

  @Test
  void retrieve_closest_buildings() {
    double latitude = 46.651947;
    double longitude = -0.249327;
    int radius = 100;

    var actual = subject.getBuildingClosest(latitude, longitude, radius);

    assertNotNull(actual.nextUrl());
    assertNull(actual.previousUrl());
    assertNotNull(actual.results());
  }

  @SneakyThrows
  @Test
  void retrieve_nearest_building() {
    double latitude = 46.651947;
    double longitude = -0.249327;
    int radius = 100;

    var actual = subject.getNearestBuildingAt(longitude, latitude, radius);

    assertEquals(0.0, actual.distance());
    assertEquals(
        List.of(BigDecimal.valueOf(-0.249000547126667), BigDecimal.valueOf(46.65198731242363)),
        actual.point().getPointCoordinates());
    assertEquals(MULTI_POLYGON, actual.shape().getType());
    var multiPolygonCoordinates = actual.shape().getMultiPolygonCoordinates();
    assertEquals(
        List.of(BigDecimal.valueOf(-0.248858521003516), BigDecimal.valueOf(46.651871841665866)),
        multiPolygonCoordinates.getFirst().getFirst().getFirst());
    assertEquals(
        List.of(BigDecimal.valueOf(-0.248858521003516), BigDecimal.valueOf(46.651871841665866)),
        multiPolygonCoordinates.getFirst().getFirst().getLast());
  }
}
