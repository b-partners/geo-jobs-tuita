package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.TileDetectionTask;
import app.bpartners.geojobs.repository.model.detection.*;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.DetectionMaskFromTileRetriever;
import app.bpartners.geojobs.service.TileDetectionTaskConsumer;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TileDetectionTaskConsumerTest {
  MachineDetectedTileRepository machineDetectedTileRepositoryMock = mock();
  TileObjectDetector objectDetectorMock = mock();
  DetectionMapper detectionMapperMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  GeometryConverter geometryConverterMock = mock();
  DetectionMaskFromTileRetriever maskRetrieverMock = mock();
  RoofCoveringDetector roofCoveringDetectorMock = mock();
  TileDetectionTaskConsumer subject =
      new TileDetectionTaskConsumer(
          machineDetectedTileRepositoryMock,
          objectDetectorMock,
          detectionMapperMock,
          detectionRepositoryMock,
          geometryConverterMock,
          maskRetrieverMock,
          roofCoveringDetectorMock);

  @Test
  void do_nothing_as_roof_polygon_not_intersecting_with_tile_polygon() {
    var detectableObjectConfigurations = List.of(mock(DetectableObjectConfiguration.class));
    var zoneDetectionJobId = randomUUID().toString();
    var parcelId = randomUUID().toString();
    var parcelJobId = randomUUID().toString();
    var tileMock = mock(Tile.class);
    var detectionMock = mock(Detection.class);
    var featureMock = mock(Feature.class);
    var featureGeometryMock = mock(FeatureGeometry.class);
    var featureMultiPolygonMock = mock(MultiPolygon.class);
    var centroidCoordinates = List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(1));
    var roofMultiPolygonMock = mock(org.locationtech.jts.geom.MultiPolygon.class);
    var maskFileMock = mock(File.class);
    var detectionResponseMock = mock(DetectionResponse.class);
    var machineDetectedTileMock = mock(MachineDetectedTile.class);
    var providedFeatureMockDomain = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var roofDelimitationMockDomain = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var roofFeatureGeometryMock =
        mock(app.bpartners.geojobs.repository.model.Feature.FeatureGeometry.class);
    var tileDetectionTask =
        TileDetectionTask.builder()
            .parcelId(parcelId)
            .zoneDetectionJobId(zoneDetectionJobId)
            .detectableObjectConfigurations(detectableObjectConfigurations)
            .tile(tileMock)
            .jobId(parcelJobId)
            .build();
    var multiPolygonFromTileMock = mock(org.locationtech.jts.geom.MultiPolygon.class);
    when(tileMock.getCoordinates()).thenReturn(new TileCoordinates().x(0).y(0).z(20));
    when(roofDelimitationMockDomain.getGeometry()).thenReturn(mock());
    when(roofMultiPolygonMock.union(any())).thenReturn(roofMultiPolygonMock);
    when(geometryConverterMock.apply(featureMultiPolygonMock.getCoordinates()))
        .thenReturn(roofMultiPolygonMock);
    when(featureGeometryMock.getMultiPolygon()).thenReturn(featureMultiPolygonMock);
    when(featureGeometryMock.getActualInstance()).thenReturn(featureMultiPolygonMock);
    when(featureMock.getGeometry()).thenReturn(featureGeometryMock);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(List.of(featureMock));
    when(roofFeatureGeometryMock.getActualInstanceStringValue())
        .thenReturn("roofGeometryActualInstanceStringValue");
    when(roofDelimitationMockDomain.getGeometry()).thenReturn(roofFeatureGeometryMock);
    when(roofMultiPolygonMock.contains(any())).thenReturn(false);
    when(roofMultiPolygonMock.intersection(any())).thenReturn(null);
    when(multiPolygonFromTileMock.contains(any())).thenReturn(false);
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(
            List.of(
                new FeatureWithDelimitation(
                    providedFeatureMockDomain, List.of(roofDelimitationMockDomain))));
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));
    when(geometryConverterMock.centroidFromGeometry(featureMultiPolygonMock))
        .thenReturn(centroidCoordinates);
    when(geometryConverterMock.getMultiPolygonFromTile(eq(0), eq(0), eq(20)))
        .thenReturn(multiPolygonFromTileMock);
    when(machineDetectedTileRepositoryMock.save(any())).thenReturn(machineDetectedTileMock);
    when(objectDetectorMock.apply(any(), any(), any())).thenReturn(detectionResponseMock);
    when(detectionMapperMock.toDetectedTile(any(), any(), any(), any(), any()))
        .thenReturn(new MachineDetectedTile());
    when(maskRetrieverMock.apply(tileMock, roofMultiPolygonMock)).thenReturn(maskFileMock);
    when(geometryConverterMock.readGeometryFromString(eq("roofGeometryActualInstanceStringValue")))
        .thenReturn(roofMultiPolygonMock);
    when(roofCoveringDetectorMock.apply(any(Tile.class), any(File.class)))
        .thenReturn(
            new RoofCoveringDetector.RoofCoveringDetectionResponse(
                new RoofCovering(RoofCoveringType.ROOF_ARDOISE, 1100L),
                new RoofCovering(RoofCoveringType.ROOF_TUILES, 1000L)));

    assertDoesNotThrow(() -> subject.accept(tileDetectionTask));

    verify(geometryConverterMock, times(1)).getMultiPolygonFromTile(eq(0), eq(0), eq(20));
    verify(machineDetectedTileRepositoryMock, never()).save(any());
    verify(objectDetectorMock, never()).apply(any(), any(), any());
    verify(detectionMapperMock, never()).toDetectedTile(any(), any(), any(), any(), any());
  }

  @Test
  void generate_mask_for_toiture_detection_and_save_machine_detected_tile() {
    var detectableObjectConfigurations = List.of(mock(DetectableObjectConfiguration.class));
    var zoneDetectionJobId = randomUUID().toString();
    var parcelId = randomUUID().toString();
    var parcelJobId = randomUUID().toString();
    var tileMock = mock(Tile.class);
    var detectionMock = mock(Detection.class);
    var featureMock = mock(Feature.class);
    var featureGeometryMock = mock(FeatureGeometry.class);
    var featureMultiPolygonMock = mock(MultiPolygon.class);
    var roofMultiPolygonMock = mock(org.locationtech.jts.geom.MultiPolygon.class);
    var maskFileMock = mock(File.class);
    var detectionResponseMock = mock(DetectionResponse.class);
    var machineDetectedTileMock = mock(MachineDetectedTile.class);
    var providedFeatureMockDomain = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var roofDelimitationMockDomain = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var roofFeatureGeometryMock =
        mock(app.bpartners.geojobs.repository.model.Feature.FeatureGeometry.class);
    var tileDetectionTask =
        TileDetectionTask.builder()
            .parcelId(parcelId)
            .zoneDetectionJobId(zoneDetectionJobId)
            .detectableObjectConfigurations(detectableObjectConfigurations)
            .tile(tileMock)
            .jobId(parcelJobId)
            .build();
    var tileBuilderMock = mock(Tile.TileBuilder.class);
    when(tileBuilderMock.detectionE2Id(any())).thenReturn(tileBuilderMock);
    when(tileBuilderMock.build()).thenReturn(tileMock);
    when(tileMock.toBuilder()).thenReturn(tileBuilderMock);
    when(tileMock.getCoordinates()).thenReturn(new TileCoordinates().x(0).y(0).z(20));
    when(roofDelimitationMockDomain.getGeometry()).thenReturn(mock());
    when(geometryConverterMock.apply(featureMultiPolygonMock.getCoordinates()))
        .thenReturn(roofMultiPolygonMock);
    when(featureGeometryMock.getMultiPolygon()).thenReturn(featureMultiPolygonMock);
    when(featureGeometryMock.getActualInstance()).thenReturn(featureMultiPolygonMock);
    when(featureMock.getGeometry()).thenReturn(featureGeometryMock);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(List.of(featureMock));
    when(roofFeatureGeometryMock.getActualInstanceStringValue())
        .thenReturn("roofGeometryActualInstanceStringValue");
    var multiPolygonFromTileMock = mock(org.locationtech.jts.geom.MultiPolygon.class);
    when(roofDelimitationMockDomain.getGeometry()).thenReturn(roofFeatureGeometryMock);
    when(roofMultiPolygonMock.contains(eq(multiPolygonFromTileMock))).thenReturn(true);
    when(multiPolygonFromTileMock.intersection(roofMultiPolygonMock))
        .thenReturn(roofMultiPolygonMock);
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(
            List.of(
                new FeatureWithDelimitation(
                    providedFeatureMockDomain, List.of(roofDelimitationMockDomain))));
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));
    when(geometryConverterMock.getMultiPolygonFromTile(0, 0, 20))
        .thenReturn(multiPolygonFromTileMock);
    when(geometryConverterMock.readGeometryFromString(eq("roofGeometryActualInstanceStringValue")))
        .thenReturn(roofMultiPolygonMock);
    when(roofMultiPolygonMock.intersection(any())).thenReturn(roofMultiPolygonMock);
    when(machineDetectedTileRepositoryMock.save(any())).thenReturn(machineDetectedTileMock);
    when(objectDetectorMock.apply(any(), any(), any())).thenReturn(detectionResponseMock);
    when(detectionMapperMock.toDetectedTile(any(), any(), any(), any(), any()))
        .thenReturn(new MachineDetectedTile());
    when(maskRetrieverMock.apply(tileMock, roofMultiPolygonMock)).thenReturn(maskFileMock);
    when(roofCoveringDetectorMock.apply(any(Tile.class), any(File.class)))
        .thenReturn(
            new RoofCoveringDetector.RoofCoveringDetectionResponse(
                new RoofCovering(RoofCoveringType.ROOF_ARDOISE, 1100L),
                new RoofCovering(RoofCoveringType.ROOF_TUILES, 1000L)));

    assertDoesNotThrow(() -> subject.accept(tileDetectionTask));

    var detectedTileCaptor = ArgumentCaptor.forClass(MachineDetectedTile.class);
    verify(geometryConverterMock, times(1)).getMultiPolygonFromTile(eq(0), eq(0), eq(20));
    verify(machineDetectedTileRepositoryMock, times(1)).save(detectedTileCaptor.capture());
    verify(objectDetectorMock)
        .apply(eq(tileDetectionTask), eq(maskFileMock), eq(detectableObjectConfigurations));
    verify(detectionMapperMock)
        .toDetectedTile(
            eq(detectionResponseMock),
            eq(tileMock),
            eq(parcelId),
            eq(zoneDetectionJobId),
            eq(parcelJobId));
  }

  @Test
  void mask_null_when_detection_not_toiture() {
    var detectableObjectConfigurations = List.of(mock(DetectableObjectConfiguration.class));
    var zoneDetectionJobId = randomUUID().toString();
    var parcelId = randomUUID().toString();
    var parcelJobId = randomUUID().toString();
    var tileMock = mock(Tile.class);
    var detectionMock = mock(Detection.class);
    var featureMock = mock(Feature.class);
    var featureGeometryMock = mock(FeatureGeometry.class);
    var featureMultiPolygonMock = mock(MultiPolygon.class);
    var centroidCoordinates = List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(1));
    var detectionResponseMock = mock(DetectionResponse.class);
    var machineDetectedTileMock = mock(MachineDetectedTile.class);
    var providedFeatureMockDomain = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var roofDelimitationMockDomain = mock(app.bpartners.geojobs.repository.model.Feature.class);
    var tileDetectionTask =
        TileDetectionTask.builder()
            .parcelId(parcelId)
            .zoneDetectionJobId(zoneDetectionJobId)
            .detectableObjectConfigurations(detectableObjectConfigurations)
            .tile(tileMock)
            .jobId(parcelJobId)
            .build();
    var tileBuilderMock = mock(Tile.TileBuilder.class);
    when(tileBuilderMock.detectionE2Id(any())).thenReturn(tileBuilderMock);
    when(tileBuilderMock.build()).thenReturn(tileMock);
    when(tileMock.toBuilder()).thenReturn(tileBuilderMock);
    when(featureGeometryMock.getMultiPolygon()).thenReturn(featureMultiPolygonMock);
    when(featureGeometryMock.getActualInstance()).thenReturn(featureMultiPolygonMock);
    when(featureMock.getGeometry()).thenReturn(featureGeometryMock);
    when(detectionMock.hasToitureModelName()).thenReturn(false);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(List.of(featureMock));
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(
            List.of(
                new FeatureWithDelimitation(
                    providedFeatureMockDomain, List.of(roofDelimitationMockDomain))));
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));
    when(geometryConverterMock.centroidFromGeometry(featureMultiPolygonMock))
        .thenReturn(centroidCoordinates);
    when(machineDetectedTileRepositoryMock.save(any())).thenReturn(machineDetectedTileMock);
    when(objectDetectorMock.apply(any(), any(), any())).thenReturn(detectionResponseMock);
    when(detectionMapperMock.toDetectedTile(any(), any(), any(), any(), any()))
        .thenReturn(new MachineDetectedTile());
    when(roofCoveringDetectorMock.apply(any(Tile.class), eq(null)))
        .thenReturn(
            new RoofCoveringDetector.RoofCoveringDetectionResponse(
                new RoofCovering(RoofCoveringType.ROOF_ARDOISE, 1100L),
                new RoofCovering(RoofCoveringType.ROOF_TUILES, 1000L)));

    assertDoesNotThrow(() -> subject.accept(tileDetectionTask));

    var detectedTileCaptor = ArgumentCaptor.forClass(MachineDetectedTile.class);
    verify(geometryConverterMock, never()).getMultiPolygonFromTile(anyInt(), anyInt(), anyInt());
    verify(machineDetectedTileRepositoryMock, times(1)).save(detectedTileCaptor.capture());
    verify(objectDetectorMock)
        .apply(eq(tileDetectionTask), eq(null), eq(detectableObjectConfigurations));
    verify(detectionMapperMock)
        .toDetectedTile(
            eq(detectionResponseMock),
            eq(tileMock),
            eq(parcelId),
            eq(zoneDetectionJobId),
            eq(parcelJobId));
  }
}
