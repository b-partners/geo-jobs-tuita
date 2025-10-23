package app.bpartners.geojobs.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.TileExtendedImageRequested;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.event.TileExtendedImageRequestedService;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointExtendedImageRequestTest {
  EventProducer eventProducerMock = mock();
  TileExtendedImageRequestedService tileExtendedImageRequestedServiceMock = mock();
  GeometryConverter geometryConverterMock = mock();
  GeometryTiledValidator geometryTiledValidatorMock = mock();
  PointExtendedImageRequest subject =
      new PointExtendedImageRequest(
          eventProducerMock,
          tileExtendedImageRequestedServiceMock,
          geometryConverterMock,
          geometryTiledValidatorMock);

  @Test
  void do_nothing_when_not_valid_tiled_geometry() {
    var detectionMock = mock(Detection.class);
    var featureGeometry =
        new FeatureGeometry(
            new Polygon()
                .coordinates(
                    List.of(
                        List.of(
                            List.of(BigDecimal.valueOf(0.0), BigDecimal.valueOf(5.0)),
                            List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(5.0)),
                            List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(0.0)),
                            List.of(BigDecimal.valueOf(0.0), BigDecimal.valueOf(0.0))))));
    var feature = new Feature().geometry(featureGeometry);
    var layer = "cite:PCRS";
    var isSynchronous = false;

    var geometryActualInstance = featureGeometry.getActualInstance();
    when(geometryTiledValidatorMock.apply(geometryActualInstance)).thenReturn(false);

    assertDoesNotThrow(() -> subject.accept(detectionMock, feature, isSynchronous));

    verify(geometryConverterMock, never()).centroidFromGeometry(any());
    verify(tileExtendedImageRequestedServiceMock, never()).accept(any());
    verify(eventProducerMock, never()).accept(any());
  }

  @Test
  void produces_tileExtendedImageRequested_when_valid_tiled_geometry_and_not_synchronous() {
    var detectionMock = mock(Detection.class);
    var featureGeometry =
        new FeatureGeometry(
            new Polygon()
                .coordinates(
                    List.of(
                        List.of(
                            List.of(BigDecimal.valueOf(0.0), BigDecimal.valueOf(5.0)),
                            List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(5.0)),
                            List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(0.0)),
                            List.of(BigDecimal.valueOf(0.0), BigDecimal.valueOf(0.0))))));
    var feature = new Feature().geometry(featureGeometry);
    var geometryActualInstance = featureGeometry.getActualInstance();
    var layer = "cite:PCRS";
    var isSynchronous = false;
    var longitude = BigDecimal.valueOf(0.25);
    var latitude = BigDecimal.valueOf(0.25);
    var defaultZoom = 20;
    var detectionIdentifier = randomUUID().toString();

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(geometryTiledValidatorMock.apply(geometryActualInstance)).thenReturn(true);
    when(geometryConverterMock.centroidFromGeometry(geometryActualInstance))
        .thenReturn(List.of(longitude, latitude));

    assertDoesNotThrow(() -> subject.accept(detectionMock, feature, isSynchronous));

    verify(geometryConverterMock).centroidFromGeometry(any());
    verify(tileExtendedImageRequestedServiceMock, never()).accept(any());
    verify(eventProducerMock)
        .accept(
            List.of(
                new TileExtendedImageRequested(
                    longitude, latitude, defaultZoom, detectionIdentifier)));
  }

  @Test
  void trigger_tileExtendedImageRequestedService_when_valid_tiled_geometry_and_is_synchronous() {
    var detectionMock = mock(Detection.class);
    var featureGeometry =
        new FeatureGeometry(
            new Polygon()
                .coordinates(
                    List.of(
                        List.of(
                            List.of(BigDecimal.valueOf(0.0), BigDecimal.valueOf(5.0)),
                            List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(5.0)),
                            List.of(BigDecimal.valueOf(5.0), BigDecimal.valueOf(0.0)),
                            List.of(BigDecimal.valueOf(0.0), BigDecimal.valueOf(0.0))))));
    var feature = new Feature().geometry(featureGeometry);
    var geometryActualInstance = featureGeometry.getActualInstance();
    var isSynchronous = true;
    var longitude = BigDecimal.valueOf(0.25);
    var latitude = BigDecimal.valueOf(0.25);
    var defaultZoom = 20;
    var detectionIdentifier = randomUUID().toString();

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(geometryTiledValidatorMock.apply(geometryActualInstance)).thenReturn(true);
    when(geometryConverterMock.centroidFromGeometry(geometryActualInstance))
        .thenReturn(List.of(longitude, latitude));

    assertDoesNotThrow(() -> subject.accept(detectionMock, feature, isSynchronous));

    verify(geometryConverterMock).centroidFromGeometry(any());
    verify(tileExtendedImageRequestedServiceMock)
        .accept(
            new TileExtendedImageRequested(longitude, latitude, defaultZoom, detectionIdentifier));
    verify(eventProducerMock, never()).accept(any());
  }
}
