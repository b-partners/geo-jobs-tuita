package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.endpoint.rest.model.CityJSONRequestStatus.PROCESSING;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.CityJSONRequestCreated;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.CityJSONRequest;
import app.bpartners.geojobs.endpoint.rest.model.CreateCityJSONRequest;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class CityJSONControllerIT extends FacadeIT {
  @Autowired CityJSONController subject;
  @Autowired FeatureMapper featureMapper;

  @MockBean AuthProvider authProviderMock;
  @MockBean BucketComponent bucketComponentMock;
  @MockBean EventProducer<CityJSONRequestCreated> eventProducerMock;

  @BeforeEach
  void setUp() {
    when(bucketComponentMock.upload(any(), any())).thenReturn(mock());
    doNothing().when(eventProducerMock).accept(anyList());

    var principal = mock(Principal.class);
    when(principal.isAdmin()).thenReturn(true);
    when(principal.getPassword()).thenReturn(randomUUID().toString());
    when(authProviderMock.getPrincipal()).thenReturn(principal);
  }

  @Test
  void should_be_processed_if_new_request() {
    var payload =
        new CreateCityJSONRequest().id(randomUUID().toString()).delimitations(List.of(feature()));

    var expected =
        new CityJSONRequest()
            .id(payload.getId())
            .delimitations(payload.getDelimitations())
            .status(PROCESSING);

    var actual = subject.processCityJSONRequest(payload, payload.getId());

    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getCityJsons(), actual.getCityJsons());
    assertEquals(PROCESSING, actual.getStatus());
  }

  @Test
  void should_throw_if_empty_delimitations_if_given() {
    var payloadId = randomUUID().toString();
    var payloadWithEmptyDelimitations =
        new CreateCityJSONRequest().id(payloadId).delimitations(List.of());

    var exception =
        assertThrows(
            BadRequestException.class,
            () -> subject.processCityJSONRequest(payloadWithEmptyDelimitations, payloadId));
    assertTrue(exception.getMessage().contains("CityJSONRequest.delimitations"));
  }

  @Test
  void should_throw_if_null_id__given() {
    var payloadWithNullDelimitations = new CreateCityJSONRequest();

    var exception =
        assertThrows(
            BadRequestException.class,
            () -> subject.processCityJSONRequest(payloadWithNullDelimitations, null));
    assertTrue(exception.getMessage().contains("CityJSONRequest.id is mandatory"));
  }

  @Test
  void should_throw_if_null_delimitations_if_given() {
    var payloadId = randomUUID().toString();
    var payloadWithNullDelimitations = new CreateCityJSONRequest().id(payloadId);

    var exception =
        assertThrows(
            BadRequestException.class,
            () -> subject.processCityJSONRequest(payloadWithNullDelimitations, payloadId));
    assertTrue(exception.getMessage().contains("CityJSONRequest.delimitations"));
  }

  @Test
  void should_get_correctly_processed_city_json_request() {
    var payloadId = randomUUID().toString();
    var payload = new CreateCityJSONRequest().id(payloadId).delimitations(List.of(feature()));

    subject.processCityJSONRequest(payload, payloadId);

    var expected =
        new CityJSONRequest()
            .id(payload.getId())
            .delimitations(payload.getDelimitations())
            .status(PROCESSING);

    var actual = subject.getById(payloadId);

    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getCityJsons(), actual.getCityJsons());
    assertEquals(PROCESSING, actual.getStatus());
  }

  private Feature feature() {
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
    var polygon = geometryFactory.createPolygon(roof1Coordinates);
    return featureMapper.toRest(polygon, 20, randomUUID().toString());
  }
}
