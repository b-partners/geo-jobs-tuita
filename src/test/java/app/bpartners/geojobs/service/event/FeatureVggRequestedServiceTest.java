package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.POLYGON;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.MOISISSURE_COULEUR;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.FeatureVggRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.model.geometry.TiledPixelPolygon;
import app.bpartners.geojobs.model.geometry.VGG;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.Parcel;
import app.bpartners.geojobs.repository.model.ParcelContent;
import app.bpartners.geojobs.repository.model.detection.*;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.DetectionVGGUpdate;
import app.bpartners.geojobs.service.PolygonCoordinatesCloser;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class FeatureVggRequestedServiceTest {
  DetectionRepository detectionRepositoryMock = mock();
  MachineDetectedTileRepository detectedTileRepositoryMock = mock();
  VGGFactory vggFactoryMock = mock();
  GeometryConverter geometryConverterMock = mock();
  TilingTaskRepository tilingTaskRepositoryMock = mock();
  DetectionVGGUpdate detectionVGGUpdateMock = mock();
  PolygonCoordinatesCloser polygonCoordinatesCloser = new PolygonCoordinatesCloser();
  TileCoordinatesPolygonIntersection tileCoordinatesPolygonIntersectionMock = mock();
  FeatureMapper featureMapperMock = mock();
  DetectionRoofPropertiesRequestedService detectionRoofPropertiesRequestedServiceMock = mock();
  TileFinder tileFinderMock = mock();

  FeatureVggRequestedService subject =
      new FeatureVggRequestedService(
          detectionRepositoryMock,
          detectedTileRepositoryMock,
          vggFactoryMock,
          geometryConverterMock,
          detectionVGGUpdateMock,
          polygonCoordinatesCloser,
          tileCoordinatesPolygonIntersectionMock,
          featureMapperMock,
          detectionRoofPropertiesRequestedServiceMock,
          tileFinderMock);

  @Test
  void compute_vgg_for_zone_and_update_detection_vgg() {
    var detectionIdentifier = randomUUID().toString();
    var zoneTilingJobIdentifier = randomUUID().toString();
    var zoneDetectionJobIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    int z = 20;
    int x = 10;
    int y = 30;
    var tileCoordinates = new TileCoordinates().z(z).x(x).y(y);
    var polygonGeoJsonZoneFeature =
        Feature.builder()
            .geometry(
                new Feature.FeatureGeometry(
                    POLYGON,
                    "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,5],[5,5],[5,0.0],[0.0,0.0]]]}"))
            .build();
    int featureNb = 0;
    var featureWithDelimitation = getFeatureWithDelimitation(polygonGeoJsonZoneFeature);
    Map<app.bpartners.geojobs.endpoint.rest.model.Feature, VGG> vggMapMock = mock();
    List<VGG> vggCollectionMock = List.of(mock(VGG.class));

    when(vggMapMock.values()).thenReturn(vggCollectionMock);
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getZdjId()).thenReturn(zoneDetectionJobIdentifier);
    when(detectionMock.getZtjId()).thenReturn(zoneTilingJobIdentifier);
    when(detectionMock.getFeatureWithDelimitations()).thenReturn(List.of(featureWithDelimitation));
    when(detectionRoofPropertiesRequestedServiceMock.applyRoofPropertiesOnDelimitation(
            anyList(), any(FeatureWithDelimitation.class)))
        .thenReturn(featureWithDelimitation);
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));
    when(tilingTaskRepositoryMock.findAllByJobId(zoneTilingJobIdentifier))
        .thenReturn(
            List.of(
                ParcelTilingTask.builder()
                    .parcels(
                        List.of(
                            Parcel.builder()
                                .parcelContent(
                                    ParcelContent.builder()
                                        .tiles(
                                            List.of(
                                                Tile.builder()
                                                    .coordinates(tileCoordinates)
                                                    .build()))
                                        .build())
                                .build()))
                    .build()));
    when(detectedTileRepositoryMock.findAllByZdjJobId(zoneDetectionJobIdentifier))
        .thenReturn(
            List.of(
                MachineDetectedTile.builder()
                    .detectedObjects(
                        List.of(
                            DetectedObject.builder()
                                .feature(
                                    app.bpartners.geojobs.repository.model.Feature.builder()
                                        .geometry(
                                            app.bpartners.geojobs.repository.model.Feature
                                                .FeatureGeometry.builder()
                                                .geometryType(MULTI_POLYGON)
                                                .actualInstanceStringValue(
                                                    "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,5],[5,5],[5,0.0],[0.0,0.0]]]]}")
                                                .build())
                                        .build())
                                .detectedObjectType(someDetectableObjectType())
                                .build()))
                    .tile(Tile.builder().coordinates(tileCoordinates).build())
                    .build()));
    var providedZoneInsideTileGeometryMock = mock(Geometry.class);
    var providedZoneAndRoofInsideTileGeometryMock = mock(Geometry.class);
    when(providedZoneInsideTileGeometryMock.intersection(any(Geometry.class)))
        .thenReturn(providedZoneAndRoofInsideTileGeometryMock);
    List<List<BigDecimal>> providedZoneAndRoofInsideTilePolygonCoordinatesMock = mock();
    when(tileCoordinatesPolygonIntersectionMock.intersects(
            eq(providedZoneAndRoofInsideTileGeometryMock), eq(tileCoordinates)))
        .thenReturn(providedZoneAndRoofInsideTilePolygonCoordinatesMock);
    when(tileCoordinatesPolygonIntersectionMock.intersection(
            any(Geometry.class), eq(tileCoordinates)))
        .thenReturn(providedZoneInsideTileGeometryMock);
    when(geometryConverterMock.convertToPolygon(
            providedZoneAndRoofInsideTilePolygonCoordinatesMock))
        .thenReturn(somePolygon());
    when(geometryConverterMock.toPolygon(any())).thenReturn(somePolygon());
    when(geometryConverterMock.apply(any())).thenReturn(someMultiPolygon());
    when(vggFactoryMock.from(anyList(), anyList())).thenReturn(vggMapMock);
    when(detectionVGGUpdateMock.apply(vggCollectionMock, detectionMock)).thenReturn(detectionMock);
    when(detectionRepositoryMock.save(detectionMock)).thenReturn(detectionMock);

    assertDoesNotThrow(
        () ->
            subject.accept(
                new FeatureVggRequested(
                    detectionIdentifier, toRestFeature(polygonGeoJsonZoneFeature), featureNb)));

    verify(detectionVGGUpdateMock, times(1)).apply(vggCollectionMock, detectionMock, featureNb);
    // TODO: add more assertions
  }

  private static @NotNull FeatureWithDelimitation getFeatureWithDelimitation(
      Feature polygonGeoJsonZoneFeature) {
    return new FeatureWithDelimitation(
        polygonGeoJsonZoneFeature,
        List.of(
            Feature.builder()
                .geometry(
                    new Feature.FeatureGeometry(
                        MULTI_POLYGON,
                        "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,10],[10,10],[10,0.0],[0.0,0.0],[0.0,10]]]]}"))
                .build()));
  }

  private @NotNull List<TiledPixelPolygon> expectedTiledPixelPolygon(
      Feature polygonGeoJsonZoneFeature, int x, int y, int z) {
    return List.of(
        new TiledPixelPolygon(
            toRestFeature(polygonGeoJsonZoneFeature),
            List.of(
                new PolygonObjectType(
                    somePolygon(), someDetectableObjectType().getDetectableType())),
            x,
            y,
            z));
  }

  private DetectableObjectType someDetectableObjectType() {
    return DetectableObjectType.builder().detectableType(MOISISSURE_COULEUR).build();
  }

  private MultiPolygon someMultiPolygon() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(0, 10),
          new Coordinate(10, 10),
          new Coordinate(10, 0),
          new Coordinate(0, 0),
          new Coordinate(0, 10)
        };
    LinearRing shell = geometryFactory.createLinearRing(coords);
    Polygon polygon = geometryFactory.createPolygon(shell);
    return geometryFactory.createMultiPolygon(new Polygon[] {polygon});
  }

  private Polygon somePolygon() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(0, 5),
          new Coordinate(5, 5),
          new Coordinate(5, 0),
          new Coordinate(0, 0),
          new Coordinate(0, 5)
        };
    LinearRing shell = geometryFactory.createLinearRing(coords);
    return geometryFactory.createPolygon(shell);
  }
}
