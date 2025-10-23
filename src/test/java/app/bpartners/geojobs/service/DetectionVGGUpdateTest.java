package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.postprocessing.BoundaryMerger;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.model.geometry.VGG;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class DetectionVGGUpdateTest {

  FileWriter fileWriterMock = mock();
  BucketComponent bucketComponentMock = mock();
  GeometryConverter geometryConverterMock = mock();
  VGGFactory vggFactoryMock = mock();
  MockedConstruction<BoundaryMerger> mergerMockedConstruction;

  @BeforeEach
  void setUp() {
    mergerMockedConstruction = mockConstruction(BoundaryMerger.class);
  }

  @AfterEach
  void tearDown() {
    mergerMockedConstruction.close();
    reset(vggFactoryMock);
    reset(geometryConverterMock);
    reset(bucketComponentMock);
    reset(fileWriterMock);
  }

  @Test
  void write_vgg_file_and_update_detection_vgg_file_key() {
    var subject =
        new DetectionVGGUpdate(
            fileWriterMock, bucketComponentMock, geometryConverterMock, vggFactoryMock);
    var vggMock = mock(VGG.class);
    var zoneDetectionJobId = randomUUID().toString();
    var zoneName = "dummy zoneName";
    var detection = Detection.builder().zoneName(zoneName).zdjId(zoneDetectionJobId).build();
    var vggAsBytesMock = new byte[0];
    var vggAsFileMock = mock(File.class);

    when(vggMock.getBytes()).thenReturn(vggAsBytesMock);
    when(fileWriterMock.write(eq(vggAsBytesMock), any(), eq(zoneName + ".geojson")))
        .thenReturn(vggAsFileMock);
    when(bucketComponentMock.upload(eq(vggAsFileMock), any())).thenReturn(mock(FileHash.class));

    var actual = subject.apply(vggMock, detection);

    assertEquals(
        detection.toBuilder()
            .vggFileKey("vgg/" + zoneDetectionJobId + "/" + zoneName + ".json")
            .build(),
        actual);
  }

  @Test
  void write_vgg_file_from_vgg_map_and_update_detection_vgg_file_key() {
    var subject =
        new DetectionVGGUpdate(
            fileWriterMock, bucketComponentMock, geometryConverterMock, vggFactoryMock);
    var vggMock = mock(VGG.class);
    var zoneDetectionJobId = randomUUID().toString();
    var zoneName = "dummy zoneName";
    var layer = "cite:PCRS";
    var vggAsBytesMock = new byte[0];
    var vggAsFileMock = mock(File.class);
    var centroidLongitude = BigDecimal.valueOf(150);
    var centroidLatitude = BigDecimal.valueOf(150);
    var domainFeature = domainFeature();
    var detection =
        Detection.builder()
            .zoneName(zoneName)
            .zdjId(zoneDetectionJobId)
            .providedGeoJsonZone(List.of(domainFeature))
            .geoServerProperties(
                new GeoServerProperties()
                    .geoServerParameter(new GeoServerParameter().layers(layer)))
            .build();
    var vggEntryValue = mock(VGG.class);
    var annotationCollectionMock =
        List.of(VGG.Annotation.builder().properties(new HashMap<>()).build());
    var tiledPolygonsMock = Set.of(mock(TiledPolygon.class));
    var mergerMock = mergerMockedConstruction.constructed().getFirst();

    when(geometryConverterMock.centroidFromGeometry(
            detection.getProvidedGeoJsonZone().getFirst().getGeometry().getActualInstance()))
        .thenReturn(List.of(centroidLongitude, centroidLatitude));

    when(vggEntryValue.values()).thenReturn(annotationCollectionMock);
    doNothing().when(vggMock).forEach((any()));
    when(vggMock.getBytes()).thenReturn(vggAsBytesMock);
    when(mergerMock.apply(vggEntryValue)).thenReturn(tiledPolygonsMock);
    when(vggFactoryMock.from(tiledPolygonsMock)).thenReturn(vggMock);
    when(fileWriterMock.write(
            eq(vggAsBytesMock),
            any(),
            eq(layer + "/vgg_" + centroidLongitude + "_" + centroidLatitude)))
        .thenReturn(vggAsFileMock);
    when(bucketComponentMock.upload(eq(vggAsFileMock), any())).thenReturn(mock(FileHash.class));

    var actual = subject.apply(Map.of(restFeature(), vggEntryValue), detection);

    assertEquals(
        detection.toBuilder()
            .providedGeoJsonZone(
                List.of(
                    domainFeature.toBuilder()
                        .properties(
                            new HashMap<>(Map.of("vgg_file_key", "cite:PCRS/vgg_150_150.json")))
                        .build()))
            .build(),
        actual);
  }

  private app.bpartners.geojobs.repository.model.Feature domainFeature() {
    var geometryStringValue =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 200, 100 ],
            [ 220, 110 ],
            [ 210, 130 ],
            [ 190, 120 ],
            [ 200, 100 ]
          ] ] ]
        }

        """;

    return app.bpartners.geojobs.repository.model.Feature.builder()
        .geometry(
            new app.bpartners.geojobs.repository.model.Feature.FeatureGeometry(
                Geometry.TypeEnum.MULTI_POLYGON, geometryStringValue))
        .properties(new HashMap<>())
        .build();
  }

  private Feature restFeature() {
    return new Feature()
        .type(FEATURE)
        .properties(new HashMap<>())
        .geometry(
            new FeatureGeometry(
                new MultiPolygon()
                    .type(MultiPolygon.TypeEnum.MULTI_POLYGON)
                    .coordinates(
                        List.of(
                            List.of(
                                List.of(
                                    List.of(BigDecimal.valueOf(200), BigDecimal.valueOf(100)),
                                    List.of(BigDecimal.valueOf(220), BigDecimal.valueOf(110)),
                                    List.of(BigDecimal.valueOf(210), BigDecimal.valueOf(130)),
                                    List.of(BigDecimal.valueOf(190), BigDecimal.valueOf(120)),
                                    List.of(BigDecimal.valueOf(200), BigDecimal.valueOf(100))))))));
  }
}
