package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.DetectionExcelFileAddressConverted;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.ZoneService;
import app.bpartners.geojobs.service.dashboard.AreaPictureApi;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureDetails;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureMapLayer;
import app.bpartners.geojobs.service.dashboard.component.CrupdateAreaPictureDetails;
import app.bpartners.geojobs.service.dashboard.mapper.AreaPictureDetailsMapper;
import app.bpartners.geojobs.service.geoserver.GeoServerConfiguration;
import app.bpartners.geojobs.sqs.EventProducerInvocationMock;
import app.bpartners.geojobs.sqs.LocalEventQueue;
import app.bpartners.geojobs.utils.detection.DetectionIT;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class DetectionExcelFileAddressConvertedIT extends DetectionIT {
  private static final int DEFAULT_EVENT_DELAY_SPEED_FACTOR = 10;
  private static final String AREA_PICTURE_LAYER = "cite:PCRS"; // Default for now
  @Autowired DetectionExcelFileAddressConvertedService subject;
  @MockBean EventProducer eventProducerMock;
  @MockBean AreaPictureApi areaPictureApiMock;
  @MockBean AreaPictureDetailsMapper areaPictureDetailsMapperMock;
  @Autowired DetectionRepository detectionRepository;
  @Autowired LocalEventQueue localEventQueue;
  @Autowired GeoServerConfiguration geoServerConfiguration;
  @Autowired CommunityAuthorizationRepository communityAuthorizationRepository;
  @MockBean ZoneService zoneService;
  EventProducerInvocationMock eventProducerInvocationMock = new EventProducerInvocationMock();
  private final String detectionId = randomUUID().toString();
  private final String communityAuthorizationId = randomUUID().toString();

  @BeforeEach
  void setUp() {
    localEventQueue.configure(
        List.of(
            new LocalEventQueue.CustomEventDelayConfig(
                DetectionAddressConversionJobStatusRecomputingSubmitted.class, 50)),
        DEFAULT_EVENT_DELAY_SPEED_FACTOR);
    doAnswer(
            invocationOnMock ->
                eventProducerInvocationMock.apply(localEventQueue, invocationOnMock))
        .when(eventProducerMock)
        .accept(any());

    var areaPictureDetailsMock = mock(AreaPictureDetails.class);
    var areaPictureMapLayerMock = mock(AreaPictureMapLayer.class);
    when(areaPictureMapLayerMock.name()).thenReturn(AREA_PICTURE_LAYER);
    when(areaPictureDetailsMock.actualLayer()).thenReturn(areaPictureMapLayerMock);
    when(areaPictureApiMock.crupdateAreaPictureDetails(any(), any(), any()))
        .thenReturn(areaPictureDetailsMock);
    when(areaPictureDetailsMapperMock.toCrupdateAreaPictureDetails(any()))
        .thenReturn(mock(CrupdateAreaPictureDetails.class));
  }

  @AfterEach
  void tearDown() {
    detectionRepository.deleteById(detectionId);
    communityAuthorizationRepository.deleteById(communityAuthorizationId);
  }

  @SneakyThrows
  @Test
  void fire_tasks_and_convert_all_address_to_detection_multi_polygon() {
    when(areaPictureDetailsMapperMock.toFeature(any(), any()))
        .thenReturn(someFeature()) // First invocation for dummy address 1
        .thenReturn(someFeature()); // Second invocation for dummy address 2
    var detection = someDetection(detectionId);

    subject.accept(DetectionExcelFileAddressConverted.builder().detection(detection).build());

    Thread.sleep(Duration.ofSeconds(5L));
    if (localEventQueue != null) localEventQueue.attemptSchedulerShutDown();

    var actualDetection = detectionRepository.findById(detection.getId()).orElseThrow();
    verify(zoneService, only()).processDetectionSteps(actualDetection);
    assertNotNull(actualDetection.getMultiPolygonGeoJsonZone());
    assertEquals(
        List.of(
            someRestFeature(),
            someRestFeature()), // restFeature as getMultiPolygonGeoJsonZone returns rest Feature
        // not domain
        actualDetection.getMultiPolygonGeoJsonZone());
    assertEquals(
        "http://dummy-geoserver.com", // Set from EnvConf
        actualDetection.getGeoServerProperties().getGeoServerUrl());
    assertEquals(
        geoServerConfiguration.defaultGeoServerProperties(null),
        actualDetection.getGeoServerProperties());
  }

  private Detection someDetection(String detectionId) {
    var e2Id = randomUUID().toString();
    var communityAuthorization = someCommunityAuthorization(communityAuthorizationId);
    return detectionRepository.save(
        Detection.builder()
            .id(detectionId)
            .endToEndId(e2Id)
            .communityOwnerId(communityAuthorization.getId())
            .zoneName("dummy zone")
            .emailReceiver("dummy receiver")
            .convertedAddresses(List.of("dummy address 1", "dummy address 2"))
            .build());
  }

  private Feature someFeature() {
    return Feature.builder()
        .zoom(20)
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(MULTI_POLYGON)
                .actualInstanceStringValue(
                    "{\"type\": \"MultiPolygon\",\"coordinates\": [ [ [ ["
                        + " 7.013274594521259, 43.550967070215918 ], ["
                        + " 7.014296384502766, 43.551202851619735 ], ["
                        + " 7.014722512163215, 43.551199530761302 ], ["
                        + " 7.014878300770262, 43.550913936251106 ], ["
                        + " 7.013448711199724, 43.550661549278466 ], ["
                        + " 7.013274594521259, 43.550967070215918 ] ] ] ]}")
                .build())
        .build();
  }

  private app.bpartners.geojobs.endpoint.rest.model.Feature someRestFeature() {
    return toRestFeature(someFeature());
  }

  private CommunityAuthorization someCommunityAuthorization(String communityAuthorizationId) {
    var apiKey = randomUUID().toString();
    return communityAuthorizationRepository.save(
        CommunityAuthorization.builder()
            .id(communityAuthorizationId)
            .apiKey(apiKey)
            .dashboardApiKey(apiKey)
            .name("dummyCommunity")
            .email("dummyCommunityEmail")
            .detectableObjectTypes(List.of())
            .maxSurface(0)
            .maxSurfaceUnit(SQUARE_METER)
            .authorizedZones(List.of())
            .build());
  }
}
