package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.repository.model.detection.DetectableType.MOISISSURE_CLAIR;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionVGGRequested;
import app.bpartners.geojobs.endpoint.event.model.ExtendedImageWithDetectedObjectRequested;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.geometry.PolygonObjectTypeSerializable;
import app.bpartners.geojobs.model.geometry.TiledPixelPolygonSerializable;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.MachineDetectedTile;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.DetectedImageDraw;
import app.bpartners.geojobs.service.GeometryTiledValidator;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import app.bpartners.geojobs.service.tiling.TiledPixelPolygonFilter;
import java.math.BigDecimal;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExtendedImageWithDetectedObjectRequestedServiceTest {
  TiledPixelPolygonFilter tiledPixelPolygonFilterMock = mock();
  TileFinder tileFinderMock = mock();
  MachineDetectedTileRepository detectedTileRepositoryMock = mock();
  BucketComponent bucketComponentMock = mock();
  DetectedImageDraw detectedImageDrawMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  GeometryConverter geometryConverterMock = mock();
  EventProducer eventProducerMock = mock();
  DetectionVGGRequestedService detectionVGGRequestedServiceMock = mock();
  GeometryTiledValidator geometryTiledValidatorMock = mock();
  ExtendedImageWithDetectedObjectRequestedService subject =
      new ExtendedImageWithDetectedObjectRequestedService(
          tileFinderMock,
          detectedTileRepositoryMock,
          bucketComponentMock,
          detectedImageDrawMock,
          detectionRepositoryMock,
          geometryConverterMock,
          eventProducerMock,
          detectionVGGRequestedServiceMock,
          geometryTiledValidatorMock);

  @Test
  void detection_not_found() {
    when(detectionRepositoryMock.findById(any())).thenReturn(Optional.empty());
    boolean isSynchronous = false;

    assertThrows(
        NoSuchElementException.class,
        () ->
            subject.accept(
                new ExtendedImageWithDetectedObjectRequested(
                    randomUUID().toString(), isSynchronous)));
  }

  @Test
  void detection_produces_detection_vgg_requested() {
    var layers = "cite:PCRS";
    var detectionId = randomUUID().toString();
    var zdjId = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    var providedFeatureMock = mock(Feature.class);
    var machineDetectedTileMock = mock(MachineDetectedTile.class);
    var pointMock = mock(Point.class);
    var featureGeometryMock = mock(FeatureGeometry.class);
    var tileCoordinatesMock = mock(TileCoordinates.class);
    var tileMock = mock(Tile.class);
    var detectedObjectMock = mock(DetectedObject.class);
    var featureToitureMultiPolygon = mock(Feature.class);
    var longitude = BigDecimal.valueOf(0);
    var latitude = BigDecimal.valueOf(1);
    int xTile = 0;
    int yTile = 0;
    int z = 20;
    var featureDetectedObjectMock = mock(Feature.class);
    var detectedFeatureGeometryMock = mock(FeatureGeometry.class);
    var detectedMultiPolygonMock = mock(MultiPolygon.class);
    var detectedPolygonMock = mock(org.locationtech.jts.geom.Polygon.class);
    var toitureConvertedMultiPolygonMock = mock(org.locationtech.jts.geom.MultiPolygon.class);
    var featureGeometryToitureMultiPolygonMock = mock(FeatureGeometry.class);
    var toitureMultiPolygonMock = mock(MultiPolygon.class);

    when(detectionMock.getId()).thenReturn(detectionId);
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layers)));
    when(detectionMock.getZdjId()).thenReturn(zdjId);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(List.of(providedFeatureMock));

    when(tileCoordinatesMock.getX()).thenReturn(xTile);
    when(tileCoordinatesMock.getY()).thenReturn(yTile);
    when(tileCoordinatesMock.getZ()).thenReturn(z);

    when(detectedFeatureGeometryMock.getMultiPolygon()).thenReturn(detectedMultiPolygonMock);
    when(featureDetectedObjectMock.getGeometry()).thenReturn(detectedFeatureGeometryMock);
    when(detectedObjectMock.getFeature()).thenReturn(featureDetectedObjectMock);
    when(detectedObjectMock.getDetectableObjectType()).thenReturn(MOISISSURE_CLAIR);

    when(tileMock.getCoordinates()).thenReturn(tileCoordinatesMock);
    when(machineDetectedTileMock.getTile()).thenReturn(tileMock);
    when(machineDetectedTileMock.getDetectedObjects()).thenReturn(List.of(detectedObjectMock));

    var pointCoordinates = List.of(longitude, latitude);
    when(pointMock.getCoordinates()).thenReturn(pointCoordinates);
    when(featureGeometryMock.getPoint()).thenReturn(pointMock);
    when(providedFeatureMock.getProperties()).thenReturn(Map.of("centroid", pointMock));
    when(providedFeatureMock.getGeometry()).thenReturn(featureGeometryMock);
    when(featureGeometryMock.getActualInstance()).thenReturn(pointMock);
    when(featureGeometryToitureMultiPolygonMock.getActualInstance())
        .thenReturn(toitureMultiPolygonMock);
    when(featureGeometryToitureMultiPolygonMock.getMultiPolygon())
        .thenReturn(toitureMultiPolygonMock);
    when(featureToitureMultiPolygon.getGeometry())
        .thenReturn(featureGeometryToitureMultiPolygonMock);
    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detectionMock));
    when(detectedTileRepositoryMock.findAllByZdjJobId(zdjId))
        .thenReturn(List.of(machineDetectedTileMock));
    when(tileFinderMock.getSurroundingTiles(eq(longitude), eq(latitude), eq(z)))
        .thenReturn(List.of(tileCoordinatesMock));
    when(geometryConverterMock.toPolygon(any())).thenReturn(detectedPolygonMock);
    when(geometryConverterMock.apply(any())).thenReturn(toitureConvertedMultiPolygonMock);
    when(tiledPixelPolygonFilterMock.filterPolygonsInMask(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    var multiPolygonStringValue = "multiPolygonStringValue";
    when(geometryConverterMock.writeGeometryAsString(any())).thenReturn(multiPolygonStringValue);
    when(geometryConverterMock.centroidFromGeometry(any())).thenReturn(pointCoordinates);
    when(geometryTiledValidatorMock.apply(any())).thenReturn(true);

    assertDoesNotThrow(
        () -> subject.accept(new ExtendedImageWithDetectedObjectRequested(detectionId, false)));

    verify(detectedTileRepositoryMock, times(1)).findAllByZdjJobId(zdjId);
    verify(tileFinderMock, times(1)).getSurroundingTiles(any(), any(), anyInt());
    verify(geometryConverterMock, times(1)).writeGeometryAsString(any());
    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    DetectionVGGRequested event = (DetectionVGGRequested) listCaptor.getValue().getFirst();
    assertEquals(
        expectedVggRequested(
            detectionId, providedFeatureMock, multiPolygonStringValue, xTile, yTile, z),
        event);
  }

  private @NotNull DetectionVGGRequested expectedVggRequested(
      String detectionId,
      Feature providedFeaturePointMock,
      String multiPolygonStringValue,
      int xTile,
      int yTile,
      int z) {
    return new DetectionVGGRequested(
        detectionId,
        List.of(
            new TiledPixelPolygonSerializable(
                providedFeaturePointMock,
                List.of(
                    new PolygonObjectTypeSerializable(multiPolygonStringValue, MOISISSURE_CLAIR)),
                xTile,
                yTile,
                z)));
  }
}
