package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.file.bucket.BucketConf;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.TileDetectionTask;
import app.bpartners.geojobs.repository.model.detection.*;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

class TileDetectionTaskConsumerIT {
  private static final String DUMMY_BUCKET_NAME = "dummyBucketName";
  private static final String TILE_BUCKET_PATCH = "tileBucketPatch";

  MachineDetectedTileRepository machineDetectedTileRepositoryMock = mock();
  TileObjectDetectorConf tileObjectDetectorConfMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  CustomBucketComponent customBucketComponentMock = mock();

  GeometryConverter geometryConverter = new GeometryConverter(null);
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  DetectionResponseAggregator detectionResponseAggregator = new DetectionResponseAggregator();
  TileValidator tileValidator = new TileValidator();
  DetectionMaskCreator maskCreator = new DetectionMaskCreator();
  GeometryPixelProjector geometryPixelProjector = new GeometryPixelProjector();
  TileCoordinatesPolygonIntersection tilePolygonIntersection =
      new TileCoordinatesPolygonIntersection(geometryPixelProjector, geometryConverter);
  DetectionMaskFromTileRetriever maskRetriever =
      new DetectionMaskFromTileRetriever(maskCreator, tilePolygonIntersection);
  DetectionMapper detectionMapper = new DetectionMapper(tileValidator);
  HttpApiTileObjectDetector objectsDetector =
      new HttpApiTileObjectDetector(
          objectMapper,
          customBucketComponentMock,
          "dummyApiUrl",
          tileObjectDetectorConfMock,
          detectionResponseAggregator);

  TileDetectionTaskConsumer subject =
      new TileDetectionTaskConsumer(
          machineDetectedTileRepositoryMock,
          objectsDetector,
          detectionMapper,
          detectionRepositoryMock,
          geometryConverter,
          maskRetriever);

  @SneakyThrows
  @Test
  void consume_detection_task_to_real_api() {
    var tileImageOriginalFile =
        new ClassPathResource("images/ALPES-MARITIMES_2024_5cm/20/544680/383095.jpg").getFile();
    var tileDetectionTaskId = randomUUID().toString();
    var tileId = randomUUID().toString();
    var detectionIdentifier = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var parcelJobId = randomUUID().toString();
    var featureForDelimitation = featureForDelimitation();
    var tile =
        Tile.builder()
            .id(tileId)
            .bucketPath(TILE_BUCKET_PATCH)
            .coordinates(new TileCoordinates().x(544680).y(383095).z(20))
            .build();
    var bucketConfMock = mock(BucketConf.class);
    var detectionMock = mock(Detection.class);

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getDetectableObjectConfigurations())
        .thenReturn(createDetectableObjectConfigurations(detectionIdentifier, detectionJobId));
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(
            List.of(
                new FeatureWithDelimitation(
                    new app.bpartners.geojobs.repository.model.Feature(),
                    List.of(featureForDelimitation))));
    when(detectionRepositoryMock.findByZdjId(detectionJobId))
        .thenReturn(Optional.of(detectionMock));
    when(bucketConfMock.getBucketName()).thenReturn(DUMMY_BUCKET_NAME);
    when(customBucketComponentMock.getBucketConf()).thenReturn(bucketConfMock);
    when(customBucketComponentMock.download(DUMMY_BUCKET_NAME, TILE_BUCKET_PATCH))
        .thenReturn(tileImageOriginalFile);
    when(tileObjectDetectorConfMock.getTileDetectionApiUrls()).thenReturn(tileDetectionApiUrls());
    when(machineDetectedTileRepositoryMock.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertDoesNotThrow(
        () ->
            subject.accept(
                TileDetectionTask.builder()
                    .id(tileDetectionTaskId)
                    .zoneDetectionJobId(detectionJobId)
                    .jobId(parcelJobId)
                    .detectableObjectConfigurations(
                        createDetectableObjectConfigurations(detectionIdentifier, detectionJobId))
                    .tile(tile)
                    .build()));

    var machineDetectedTileCaptor = ArgumentCaptor.forClass(MachineDetectedTile.class);
    verify(machineDetectedTileRepositoryMock, times(1)).save(machineDetectedTileCaptor.capture());
    var actual = machineDetectedTileCaptor.getValue();
    assertEquals(12, actual.getDetectedObjects().size());

    assertEquals(
        expectedDetectedObjectTypes(),
        (actual.getDetectedObjects().stream()
            .map(DetectedObject::getDetectableObjectType)
            .collect(Collectors.toSet())));
  }

  private Set<DetectableType> expectedDetectedObjectTypes() {
    return Set.of(MOISISSURE_NOIRCIE, MOISISSURE_CLAIR, MOISISSURE_COULEUR, CHEMINEE, BATI_TUILES);
  }

  @SneakyThrows
  private String tileDetectionApiUrls() {
    return Files.readString(
        Path.of(new ClassPathResource("conf/tileObjectDetectorConf.json").getFile().getPath()));
  }

  private app.bpartners.geojobs.repository.model.Feature featureForDelimitation() {
    app.bpartners.geojobs.repository.model.Feature.FeatureGeometry geometry =
        new app.bpartners.geojobs.repository.model.Feature.FeatureGeometry();

    geometry.setGeometryType(Geometry.TypeEnum.MULTI_POLYGON);
    geometry.setActualInstanceStringValue(
        """
{
  "type": "MultiPolygon",
  "coordinates": [
    [
      [
        [7.001380920410156, 43.55065076822822],
        [7.001271383246328, 43.550651153395584],
        [7.001269834513988, 43.550646706078226],
        [7.001241150953786, 43.55062614280176],
        [7.001235036387186, 43.550627269241545],
        [7.001234033877726, 43.55061289199318],
        [7.00125133009923, 43.55061225397956],
        [7.00124907444506, 43.55057990517163],
        [7.001235484564402, 43.55058040646799],
        [7.001233497239312, 43.550569669104384],
        [7.001158197839128, 43.55057334756624],
        [7.00116400714788, 43.55063889819283],
        [7.001143004582222, 43.55063967290642],
        [7.001150299949268, 43.55070877227807],
        [7.001380920410156, 43.550711491964144],
        [7.001380920410156, 43.55065076822822]
      ]
    ]
  ]
}
""");

    app.bpartners.geojobs.repository.model.Feature feature =
        new app.bpartners.geojobs.repository.model.Feature();
    feature.setProperties(new java.util.HashMap<>());
    feature.setGeometry(geometry);
    return feature;
  }

  private List<DetectableObjectConfiguration> createDetectableObjectConfigurations(
      String detectionIdentifier, String detectionJobId) {
    var detectableObjectTypes =
        new DetectableObjectTypeMapper()
            .mapFromModel(new DetectableObjectModel().modelName(TOITURE));
    return detectableObjectTypes.stream()
        .map(
            detectableObjectType -> {
              var detectableObjectConfigurationId = randomUUID().toString();
              return new DetectableObjectConfiguration(
                  detectableObjectConfigurationId,
                  detectionJobId,
                  detectionIdentifier,
                  DetectableType.valueOf(detectableObjectType.getValue()),
                  null,
                  0.0);
            })
        .toList();
  }
}
