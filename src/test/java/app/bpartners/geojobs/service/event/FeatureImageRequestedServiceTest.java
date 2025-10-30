package app.bpartners.geojobs.service.event;

import static java.lang.System.currentTimeMillis;
import static java.util.UUID.randomUUID;
import static javax.imageio.ImageIO.read;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;

import app.bpartners.geojobs.endpoint.event.model.FeatureImageRequested;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.file.WhiteImageDetector;
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
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class FeatureImageRequestedServiceTest {
  public static final double ONE_KILOMETRE_AREA = 1_000_000.0;
  DetectionRepository detectionRepositoryMock = mock();
  GeometryConverter geometryConverterMock = mock();
  BucketComponent bucketComponentMock = mock();
  TileImagesAssembler tileImageAssemblerMock = mock();
  TilingTaskRepository tilingTaskRepositoryMock = mock();
  GeometrySquareMeterArea geometrySquareMeterAreaMock = mock();
  TileImageBlur tileImageBlurMock = mock();
  WhiteImageDetector whiteImageDetectorMock = mock();
  TileFinder tileFinderMock = mock();
  FeatureImageRequestedService subject =
      new FeatureImageRequestedService(
          detectionRepositoryMock,
          geometryConverterMock,
          bucketComponentMock,
          tileImageAssemblerMock,
          tilingTaskRepositoryMock,
          geometrySquareMeterAreaMock,
          tileImageBlurMock,
          whiteImageDetectorMock,
          tileFinderMock);

  @BeforeEach
  void setUp() {
    when(tileImageBlurMock.apply(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(whiteImageDetectorMock.apply(any())).thenReturn(false);
    List<TileCoordinates> tileCoordinatesMock = mock();
    when(tileCoordinatesMock.contains(any(TileCoordinates.class))).thenReturn(true);
    when(tileFinderMock.getFromGeoJsonPolygon(any(), anyInt())).thenReturn(tileCoordinatesMock);
  }

  @Test
  void terminate_and_warn_when_zone_area_greater_than_one_kilometre_square() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));
    var polygonGeometryMock = mock(org.locationtech.jts.geom.Polygon.class);
    when(geometryConverterMock.convertToPolygon(any())).thenReturn(polygonGeometryMock);
    when(geometrySquareMeterAreaMock.apply(polygonGeometryMock)).thenReturn(ONE_KILOMETRE_AREA + 1);

    assertDoesNotThrow(
        () -> subject.accept(new FeatureImageRequested(detectionIdentifier, getFeature(), 0)));
    verify(tilingTaskRepositoryMock, never()).findAllByJobId(any());
    verify(bucketComponentMock, never()).download(any());
    verify(tileImageAssemblerMock, never()).apply(any());
    verify(bucketComponentMock, never()).upload(any(), any());
  }

  private static Feature getFeature() {
    return new Feature()
        .geometry(
            new FeatureGeometry(
                new Polygon()
                    .coordinates(
                        List.of(
                            List.of(
                                List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(10)),
                                List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(10)),
                                List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0)),
                                List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(0)),
                                List.of(BigDecimal.valueOf(0), BigDecimal.valueOf(10)))))));
  }

  @Test
  void process_image_assembling_and_upload_when_zone_area_less_than_one_kilometre_square() {
    var detectionIdentifier = randomUUID().toString();
    var tilingJobIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    var imageFileMock = mock(File.class);
    var assembleImageFileMock = mock(File.class);
    var randomBucketPath = randomUUID().toString();
    var randomZoneName = "random zone name " + currentTimeMillis();

    MockedStatic<ImageIO> imageIOMockedStatic = mockStatic(ImageIO.class);
    imageIOMockedStatic.when(() -> read(any(File.class))).thenReturn(mock(BufferedImage.class));
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getZtjId()).thenReturn(tilingJobIdentifier);
    when(detectionMock.getZoneName()).thenReturn(randomZoneName);
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));
    var polygonGeometryMock = mock(org.locationtech.jts.geom.Polygon.class);
    when(geometryConverterMock.retrievePolygonGeometry(any())).thenReturn(polygonGeometryMock);
    when(geometrySquareMeterAreaMock.apply(polygonGeometryMock)).thenReturn(ONE_KILOMETRE_AREA);
    var tilesWithImagesMock =
        List.of(
            Tile.builder()
                .coordinates(new TileCoordinates())
                .bucketPath(randomBucketPath)
                .image(imageFileMock)
                .build());
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
    when(bucketComponentMock.upload(
            assembleImageFileMock,
            "zone_images/" + detectionIdentifier + "/" + 0 + "/" + randomZoneName + ".jpg"))
        .thenReturn(mock(FileHash.class));

    assertDoesNotThrow(
        () -> subject.accept(new FeatureImageRequested(detectionIdentifier, getFeature(), 0)));

    verify(tilingTaskRepositoryMock).findAllByJobId(tilingJobIdentifier);
    verify(bucketComponentMock).download(randomBucketPath);
    verify(tileImageAssemblerMock).apply(tilesWithImagesMock);
    verify(bucketComponentMock)
        .upload(assembleImageFileMock, "zone_images/" + detectionIdentifier + ".jpg");
    verify(bucketComponentMock)
        .upload(
            assembleImageFileMock,
            "zone_images/" + detectionIdentifier + "/" + 0 + "/" + randomZoneName + ".jpg");
    imageIOMockedStatic.close();
  }
}
