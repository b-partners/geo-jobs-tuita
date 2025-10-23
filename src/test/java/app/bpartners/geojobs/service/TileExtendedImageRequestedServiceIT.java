package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum.ZONE;
import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.TileExtendedImageRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.event.TileExtendedImageRequestedService;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import app.bpartners.geojobs.utils.ImageComparator;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.core.io.ClassPathResource;

class TileExtendedImageRequestedServiceIT {
  BucketComponent bucketComponentMock = mock(BucketComponent.class);
  TileFinder tileFinder = new TileFinder();
  GeometryPixelProjector geometryPixelProjector = new GeometryPixelProjector();
  GeometryConverter geometryConverter = new GeometryConverter(mock());
  FilePolygonDrawer filePolygonDrawer = new FilePolygonDrawer();
  DetectionBackgroundRetriever detectionBackgroundRetriever =
      mock(DetectionBackgroundRetriever.class);
  DetectionProvidedZoneUnifier detectionProvidedZoneUnifier =
      new DetectionProvidedZoneUnifier(geometryConverter);
  TileImagesAssembler tileImagesAssembler = new TileImagesAssembler();
  DetectionRepository detectionRepositoryMock = mock();
  TileImageBlur tileImageBlur =
      new TileImageBlur(
          geometryPixelProjector,
          geometryConverter,
          filePolygonDrawer,
          detectionBackgroundRetriever,
          detectionProvidedZoneUnifier);
  ImageComparator imageComparator = new ImageComparator();

  TileExtendedImageRequestedService subject =
      new TileExtendedImageRequestedService(
          tileFinder,
          bucketComponentMock,
          detectionRepositoryMock,
          tileImageBlur,
          tileImagesAssembler);

  @SneakyThrows
  @BeforeEach
  void setUp() {
    when(bucketComponentMock.download(any()))
        .thenReturn(new ClassPathResource("/images/extender/61-92.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/62-92.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/63-92.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/61-93.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/62-93.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/63-93.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/61-94.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/62-94.jpg").getFile())
        .thenReturn(new ClassPathResource("/images/extender/63-94.jpg").getFile());

    when(bucketComponentMock.upload(any(), any())).thenReturn(mock(FileHash.class));
  }

  @Test
  void extend_image() {
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var layer = "cite:PCRS";
    var zoomLevel = HOUSES_0.getZoomLevel();
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(app.bpartners.geojobs.repository.model.detection.Detection.class);
    var repoFeatureMock = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var unifiedRoofMultiPolygonMock = mock(MultiPolygon.class);
    var featureWithDelimitation =
        new FeatureWithDelimitation(repoFeatureMock, List.of(repoFeatureMock));
    var geometryFactory = new org.locationtech.jts.geom.GeometryFactory().createMultiPolygon(null);

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getGeoJsonDelimitationType()).thenReturn(ZONE);
    when(detectionMock.getFeatureWithDelimitations()).thenReturn(List.of(featureWithDelimitation));
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layer)));
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(java.util.Optional.of(detectionMock));
    when(detectionBackgroundRetriever.apply(detectionMock)).thenReturn(geometryFactory);
    when(unifiedRoofMultiPolygonMock.intersection(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(unifiedRoofMultiPolygonMock.difference(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    try (MockedStatic<FeatureMapper> mockedStatic = mockStatic(FeatureMapper.class)) {
      var restFeatureMock = mock(app.bpartners.geojobs.endpoint.rest.model.Feature.class);
      var geometryMock = mock(FeatureGeometry.class);
      var polygonMock = mock(Polygon.class);
      when(polygonMock.getCoordinates())
          .thenReturn(
              List.of(
                  List.of(
                      List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(0)),
                      List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(1)),
                      List.of(BigDecimal.valueOf(1), BigDecimal.valueOf(1)),
                      List.of(BigDecimal.valueOf(1), BigDecimal.valueOf(0)),
                      List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(0)))));
      when(geometryMock.getActualInstance()).thenReturn(polygonMock);
      when(restFeatureMock.getGeometry()).thenReturn(geometryMock);
      mockedStatic.when(() -> FeatureMapper.toRestFeature(any())).thenReturn(restFeatureMock);

      assertDoesNotThrow(
          () ->
              subject.accept(
                  new TileExtendedImageRequested(
                      longitude, latitude, zoomLevel, detectionIdentifier)));

      var fileCaptor = ArgumentCaptor.forClass(File.class);
      var stringCaptor = ArgumentCaptor.forClass(String.class);
      verify(bucketComponentMock).upload(fileCaptor.capture(), stringCaptor.capture());
      var extendedFile = fileCaptor.getValue();
      var extendedFileKey = stringCaptor.getValue();
      var expectedKey =
          layer
              + "/extended_original_"
              + longitude.doubleValue()
              + "_"
              + latitude.doubleValue()
              + ".jpg";

      assertEquals(expectedKey, extendedFileKey);
      assertTrue(imageComparator.apply(expectedAssembledImage(), extendedFile));
    }
  }

  @SneakyThrows
  private File expectedAssembledImage() {
    return new ClassPathResource("/images/expected_3x3_assemble_image.jpg").getFile();
  }
}
