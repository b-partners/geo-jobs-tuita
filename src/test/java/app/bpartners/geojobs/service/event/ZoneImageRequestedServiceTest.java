package app.bpartners.geojobs.service.event;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.ZoneImageRequested;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.Parcel;
import app.bpartners.geojobs.repository.model.ParcelContent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.TileImageBlur;
import app.bpartners.geojobs.service.TileImagesAssembler;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZoneImageRequestedServiceTest {
  public static final double ONE_KILOMETRE_AREA = 1_000_000.0;
  DetectionRepository detectionRepositoryMock = mock();
  GeometryConverter geometryConverterMock = mock();
  BucketComponent bucketComponentMock = mock();
  TileImagesAssembler tileImageAssemblerMock = mock();
  TilingTaskRepository tilingTaskRepositoryMock = mock();
  GeometrySquareMeterArea geometrySquareMeterAreaMock = mock();
  TileImageBlur tileImageBlurMock = mock();
  ZoneImageRequestedService subject =
      new ZoneImageRequestedService(
          detectionRepositoryMock,
          geometryConverterMock,
          bucketComponentMock,
          tileImageAssemblerMock,
          tilingTaskRepositoryMock,
          geometrySquareMeterAreaMock,
          tileImageBlurMock);

  @BeforeEach
  void setUp() {
    when(tileImageBlurMock.apply(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
  }

  @Test
  void terminate_and_warn_when_zone_area_greater_than_one_kilometre_square() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    when(detectionMock.getPolygonGeoJsonZone())
        .thenReturn(
            new Feature()
                .geometry(
                    new FeatureGeometry(
                        new Polygon()
                            .coordinates(
                                List.of(
                                    List.of(
                                        List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(1))))))));
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));
    var polygonGeometryMock = mock(org.locationtech.jts.geom.Polygon.class);
    when(geometryConverterMock.convertToPolygon(any())).thenReturn(polygonGeometryMock);
    when(geometrySquareMeterAreaMock.apply(polygonGeometryMock)).thenReturn(ONE_KILOMETRE_AREA + 1);

    assertDoesNotThrow(() -> subject.accept(new ZoneImageRequested(detectionIdentifier)));
    verify(tilingTaskRepositoryMock, never()).findAllByJobId(any());
    verify(bucketComponentMock, never()).download(any());
    verify(tileImageAssemblerMock, never()).apply(any());
    verify(bucketComponentMock, never()).upload(any(), any());
  }

  @Test
  void process_image_assembling_and_upload_when_zone_area_less_than_one_kilometre_square() {
    var detectionIdentifier = randomUUID().toString();
    var tilingJobIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    var imageFileMock = mock(File.class);
    var assembleImageFileMock = mock(File.class);
    var randomBucketPath = randomUUID().toString();

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getZtjId()).thenReturn(tilingJobIdentifier);
    when(detectionMock.getPolygonGeoJsonZone())
        .thenReturn(
            new Feature()
                .geometry(
                    new FeatureGeometry(
                        new Polygon()
                            .coordinates(
                                List.of(
                                    List.of(
                                        List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(1))))))));
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));
    var polygonGeometryMock = mock(org.locationtech.jts.geom.Polygon.class);
    when(geometryConverterMock.convertToPolygon(any())).thenReturn(polygonGeometryMock);
    when(geometrySquareMeterAreaMock.apply(polygonGeometryMock)).thenReturn(ONE_KILOMETRE_AREA);
    var tilesWithImagesMock =
        List.of(Tile.builder().bucketPath(randomBucketPath).image(imageFileMock).build());
    when(tilingTaskRepositoryMock.findAllByJobId(tilingJobIdentifier))
        .thenReturn(
            List.of(
                ParcelTilingTask.builder()
                    .parcels(
                        List.of(
                            Parcel.builder()
                                .parcelContent(
                                    ParcelContent.builder().tiles(tilesWithImagesMock).build())
                                .build()))
                    .build()));
    when(bucketComponentMock.download(randomBucketPath)).thenReturn(imageFileMock);
    when(tileImageAssemblerMock.apply(tilesWithImagesMock)).thenReturn(assembleImageFileMock);
    when(bucketComponentMock.upload(
            assembleImageFileMock, "zone_images/" + detectionIdentifier + ".jpg"))
        .thenReturn(mock(FileHash.class));

    assertDoesNotThrow(() -> subject.accept(new ZoneImageRequested(detectionIdentifier)));

    verify(tilingTaskRepositoryMock).findAllByJobId(tilingJobIdentifier);
    verify(bucketComponentMock).download(randomBucketPath);
    verify(tileImageAssemblerMock).apply(tilesWithImagesMock);
    verify(bucketComponentMock)
        .upload(assembleImageFileMock, "zone_images/" + detectionIdentifier + ".jpg");
  }
}
