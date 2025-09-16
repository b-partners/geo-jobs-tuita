package app.bpartners.geojobs.unit;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.MACHINE_DETECTION;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.SUCCEEDED;
import static app.bpartners.geojobs.endpoint.rest.model.Status.ProgressionEnum.FINISHED;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionProcessSucceeded;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionStepStatisticMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.RoofDelimiterMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.StatusMapper;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Prospect;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.model.geometry.VGG;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.MachineDetectedTile;
import app.bpartners.geojobs.service.*;
import app.bpartners.geojobs.service.detection.DetectionMapper;
import app.bpartners.geojobs.service.detection.DetectionMaskCreator;
import app.bpartners.geojobs.service.detection.DetectionResponse;
import app.bpartners.geojobs.service.detection.TileObjectDetector;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import app.bpartners.geojobs.service.tiling.TileValidator;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;
import org.mockito.ArgumentCaptor;

@Disabled("TODO: flaky test on CI")
class RooferDetectionServiceTest {
  private static final String PRESIGNED_URL = "https://presigned.bucket.org";
  TileObjectDetector detector = mock();
  DetectionMaskCreator detectionMaskCreator = new DetectionMaskCreator();
  TileValidator tileValidator = new TileValidator();
  DetectionMapper detectionMapper = new DetectionMapper(tileValidator);
  MachineDetectedTileRepository machineDetectedTileRepository = mock();
  VGGFactory vggFactoryMock = mock();
  FileWriter fileWriter = mock();
  DetectionRepository detectionRepository = mock();
  BucketComponent bucketComponent = mock();
  EventProducer<GeoJsonConversionProcessSucceeded> eventProducer = mock();
  StatusMapper<JobStatus> statusMapper = new StatusMapper<>();
  DetectionFeaturesResultImageRetriever featureImageRetrieverMock =
      mock(DetectionFeaturesResultImageRetriever.class);
  DetectionStepStatisticMapper detectionStepStatisticMapper =
      new DetectionStepStatisticMapper(statusMapper);
  DetectionImageAttributeRetriever imageAttributeRetrieverMock =
      mock(DetectionImageAttributeRetriever.class);
  DetectionVggAttributeRetriever vggAttributeRetrieverMock =
      mock(DetectionVggAttributeRetriever.class);
  RoofDelimiterMapper roofDelimiterMapperMock = mock();
  DetectionFromStatisticRestMapper detectionFromStatisticRestMapper =
      new DetectionFromStatisticRestMapper(
          bucketComponent,
          detectionStepStatisticMapper,
          featureImageRetrieverMock,
          imageAttributeRetrieverMock,
          vggAttributeRetrieverMock,
          roofDelimiterMapperMock);
  Mailer mailer = mock();
  AuthProvider authProvider = mock();
  HTMLTemplateParser htmlTemplateParser = new HTMLTemplateParser();
  BuildingApi buildingApiMock = mock();
  DetectionVGGUpdate detectionVGGUpdate =
      new DetectionVGGUpdate(
          fileWriter, bucketComponent, new GeometryConverter(buildingApiMock), vggFactoryMock);
  RooferDetectionService subject;

  @BeforeEach
  void setUp() {
    subject =
        new RooferDetectionService(
            detector,
            detectionMaskCreator,
            detectionMapper,
            machineDetectedTileRepository,
            vggFactoryMock,
            detectionRepository,
            eventProducer,
            detectionFromStatisticRestMapper,
            mailer,
            authProvider,
            htmlTemplateParser,
            detectionVGGUpdate);

    when(featureImageRetrieverMock.apply(any()))
        .thenAnswer(
            invocation ->
                ((app.bpartners.geojobs.repository.model.detection.Detection)
                        invocation.getArgument(0))
                    .getProvidedGeoJsonZone());
    when(detector.apply(any(), any(), any()))
        .thenReturn(
            DetectionResponse.builder()
                .rstRaw(
                    Map.of(
                        randomUUID().toString(),
                        DetectionResponse.ImageData.builder().regions(Map.of()).build()))
                .build());
    when(machineDetectedTileRepository.save(any())).thenReturn(new MachineDetectedTile());
    when(vggFactoryMock.from(any(Polygon.class), any())).thenReturn(new VGG());
    when(fileWriter.write(any(), any(), any())).thenReturn(mock(File.class));
    when(bucketComponent.presign(any(String.class))).thenReturn(PRESIGNED_URL);
    when(authProvider.getAuthenticatedCommunity())
        .thenReturn(CommunityAuthorization.builder().email("test@gmail.com").build());
    when(detectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void apply_ok() {
    var actual = subject.apply(detection());

    verify(bucketComponent, times(1)).upload(any(), any());
    verify(eventProducer, only()).accept(any());
    verify(detectionRepository, times(1)).save(any());

    assertEquals(PRESIGNED_URL, actual.getVggUrl());
    assertEquals(MACHINE_DETECTION, actual.getStep().getName());
    assertEquals(FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void send_mail() throws AddressException {
    var prospect = new Prospect();
    var file = mock(File.class);
    var emailCaptor = ArgumentCaptor.forClass(Email.class);

    subject.sendEmail(prospect, file);

    verify(mailer, only()).accept(emailCaptor.capture());

    assertTrue(emailCaptor.getValue().cc().contains(new InternetAddress("tech@birdia.fr")));
  }

  Detection detection() {
    return Detection.builder()
        .id(randomUUID().toString())
        .isRooferMade(true)
        .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
        .zoneName("Dijon")
        .imageFileKey(randomUUID().toString())
        .providedGeoJsonZone(List.of(toDomainFeature(feature())))
        .build();
  }

  app.bpartners.geojobs.endpoint.rest.model.Feature feature() {
    var coordinates =
        List.of(
            List.of(new BigDecimal("465.95744680851067"), new BigDecimal("282.97872340425533")),
            List.of(new BigDecimal("780.8510638297872"), new BigDecimal("421.2765957446809")),
            List.of(new BigDecimal("619.1489361702128"), new BigDecimal("800")),
            List.of(new BigDecimal("474.468085106383"), new BigDecimal("729.7872340425532")),
            List.of(new BigDecimal("510.63829787234044"), new BigDecimal("636.1702127659574")),
            List.of(new BigDecimal("351.06382978723406"), new BigDecimal("557.4468085106383")),
            List.of(new BigDecimal("465.95744680851067"), new BigDecimal("282.97872340425533")));
    MultiPolygon multiPolygon = new MultiPolygon().coordinates(List.of(List.of(coordinates)));
    multiPolygon.setType(MultiPolygon.TypeEnum.MULTI_POLYGON);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("id", randomUUID().toString());
    properties.put("zoom", 20);
    return new app.bpartners.geojobs.endpoint.rest.model.Feature()
        .properties(properties)
        .geometry(new FeatureGeometry(multiPolygon));
  }
}
