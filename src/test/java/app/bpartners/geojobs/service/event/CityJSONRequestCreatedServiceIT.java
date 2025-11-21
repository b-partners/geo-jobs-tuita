package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.cityjson.CityJSONRequestStatus.*;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.model.CityJSONRequestCreated;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.CityJSONRequestRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.cityjson.CityJSONRequest;
import app.bpartners.geojobs.service.cityjson.LidarDataToCityJsonProcessor;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor.RoofsAnalysisResult;
import app.bpartners.geojobs.service.lidar.model.LidarDataStatus;
import app.bpartners.geojobs.service.lidar.model.geometry.roof.LidarRoofData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class CityJSONRequestCreatedServiceIT extends FacadeIT {
  @MockBean BucketComponent bucketComponentMock;
  @Autowired CityJSONRequestCreatedService subject;
  @MockBean LidarRoofsAnalysisProcessor lidarProcessorMock;
  @MockBean LidarDataToCityJsonProcessor cityJsonProcessorMock;
  @Autowired FeatureMapper featureMapper;
  @Autowired CityJSONRequestRepository cityJSONRequestRepository;

  private static final String REQUEST_ID = randomUUID().toString();

  @BeforeEach()
  void setUp() {
    when(bucketComponentMock.upload(any(), any())).thenReturn(mock());
    cityJSONRequestRepository.save(request());
  }

  @AfterEach()
  void cleanUp() {
    cityJSONRequestRepository.deleteById(REQUEST_ID);
  }

  @Test
  void generate_ok() {
    when(lidarProcessorMock.apply(anySet())).thenReturn(analysisResult(LidarDataStatus.AVAILABLE));
    when(cityJsonProcessorMock.apply(any(), any())).thenReturn(mock());

    subject.accept(CityJSONRequestCreated.builder().requestId(REQUEST_ID).build());

    var actualRequest = cityJSONRequestRepository.findById(REQUEST_ID).orElseThrow();
    assertEquals(FINISHED, actualRequest.getStatus());
  }

  @Test
  void should_be_unavailable_when_lidar_data_is_unavailable() {
    when(lidarProcessorMock.apply(anySet()))
        .thenReturn(analysisResult(LidarDataStatus.UNAVAILABLE));

    subject.accept(CityJSONRequestCreated.builder().requestId(REQUEST_ID).build());

    var actualRequest = cityJSONRequestRepository.findById(REQUEST_ID).orElseThrow();

    assertEquals(UNAVAILABLE, actualRequest.getStatus());
    verify(cityJsonProcessorMock, never()).apply(any(), any());
  }

  @Test
  void should_be_failed_when_lidar_data_is_extraction_error() {
    when(lidarProcessorMock.apply(anySet()))
        .thenReturn(analysisResult(LidarDataStatus.EXTRACTION_ERROR));

    subject.accept(CityJSONRequestCreated.builder().requestId(REQUEST_ID).build());

    var actualRequest = cityJSONRequestRepository.findById(REQUEST_ID).orElseThrow();

    assertEquals(FAILED, actualRequest.getStatus());
    verify(cityJsonProcessorMock, never()).apply(any(), any());
  }

  @Test
  void should_be_failed_when_exception_is_raised() {
    when(lidarProcessorMock.apply(anySet())).thenThrow(new RuntimeException());

    subject.accept(CityJSONRequestCreated.builder().requestId(REQUEST_ID).build());

    var actualRequest = cityJSONRequestRepository.findById(REQUEST_ID).orElseThrow();

    assertEquals(FAILED, actualRequest.getStatus());
    verify(cityJsonProcessorMock, never()).apply(any(), any());
  }

  private static RoofsAnalysisResult analysisResult(LidarDataStatus status) {
    return new RoofsAnalysisResult(
        Map.of(randomUUID().toString(), LidarRoofData.builder().status(status).build()));
  }

  private CityJSONRequest request() {
    return CityJSONRequest.builder()
        .id(REQUEST_ID)
        .delimitations(List.of(roofFeature()))
        .creationDatetime(now())
        .build();
  }

  private Feature roofFeature() {
    var restFeature = featureMapper.toRest(roofPolygon(), 20, randomUUID().toString());
    return toDomainFeature(restFeature);
  }

  private Polygon roofPolygon() {
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
    return geometryFactory.createPolygon(roof1Coordinates);
  }
}
