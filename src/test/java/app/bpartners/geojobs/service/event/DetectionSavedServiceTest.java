package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.service.event.DetectionSavedService.DETECTION_SAVED_TEMPLATE;
import static app.bpartners.geojobs.service.event.DetectionSavedService.computeStaticEmailBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.endpoint.rest.model.GeoServerParameter;
import app.bpartners.geojobs.endpoint.rest.model.GeoServerProperties;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.GeoServerParameterStringMapValue;
import app.bpartners.geojobs.service.detection.DetectableObjectModelMapper;
import app.bpartners.geojobs.service.detection.DetectionGeoServerParameterModelMapper;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;

class DetectionSavedServiceTest {
  BucketComponent bucketComponentMock = mock();
  Mailer mailerMock = mock();
  DetectableObjectModelMapper detectableObjectModelMapper = new DetectableObjectModelMapper();
  DetectionGeoServerParameterModelMapper detectionGeoServerParameterModelMapper =
      new DetectionGeoServerParameterModelMapper();
  CommunityAuthorizationRepository communityAuthorizationRepositoryMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  DetectionSavedService subject =
      new DetectionSavedService(
          mailerMock,
          bucketComponentMock,
          detectionGeoServerParameterModelMapper,
          communityAuthorizationRepositoryMock,
          detectionRepositoryMock);

  @BeforeEach
  void setUp() {
    var communityAuthorizationMock = mock(CommunityAuthorization.class);

    when(communityAuthorizationMock.getId()).thenReturn("communityId");
    when(communityAuthorizationMock.getName()).thenReturn("communityName");
    when(communityAuthorizationMock.getEmail()).thenReturn("communityEmail");
    when(communityAuthorizationRepositoryMock.findById(any()))
        .thenReturn(Optional.of(communityAuthorizationMock));
  }

  @SneakyThrows
  @Test
  void accept_ok() {
    when(bucketComponentMock.presign(any(), any())).thenReturn(new URI("http://localhost").toURL());
    var shapeFileKey = "dummy";
    var geoServerParameter =
        new GeoServerParameter()
            .service("service_value_test")
            .request("request_value_test")
            .layers("layers_value_test")
            .styles("styles_value_test")
            .format("format_value_test")
            .transparent(true)
            .version("version_value_test")
            .width(1)
            .height(1)
            .srs("srs_value_test");
    var detection =
        Detection.builder()
            .shapeFileKey(shapeFileKey)
            .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
            .geoServerProperties(
                new GeoServerProperties()
                    .geoServerUrl("geoServerUrl")
                    .geoServerParameter(geoServerParameter))
            .build();
    when(detectionRepositoryMock.findById(detection.getId())).thenReturn(Optional.of(detection));
    List<InternetAddress> cc = List.of();
    List<InternetAddress> bcc = List.of();
    String htmlBody =
        computeStaticEmailBody(
            detection,
            bucketComponentMock,
            detectionGeoServerParameterModelMapper,
            communityAuthorizationRepositoryMock);
    List<File> attachments = List.of();

    subject.accept(DetectionSaved.builder().detectionIdentifier(detection.getId()).build());

    var emailCaptor = ArgumentCaptor.forClass(Email.class);
    var durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mailerMock, only()).accept(emailCaptor.capture());
    verify(bucketComponentMock, times(2)).presign(eq(shapeFileKey), durationCaptor.capture());
    var urlDurationValue = durationCaptor.getValue();
    var actualEmail = emailCaptor.getValue();
    var expectedMail =
        new Email(
            new InternetAddress("tech@birdia.fr"),
            cc,
            bcc,
            actualEmail.subject(),
            htmlBody,
            attachments);
    assertEquals(expectedMail, actualEmail);
    assertEquals(Duration.ofHours(24L), urlDurationValue);
  }

  @Test
  void parse_detection_saved_email_body() {
    HTMLTemplateParser htmlTemplateParser = new HTMLTemplateParser();
    Context context = new Context();
    var geoServerParameter =
        new GeoServerParameter()
            .service("service_value_test")
            .request("request_value_test")
            .layers("layers_value_test")
            .styles("styles_value_test")
            .format("format_value_test")
            .transparent(true)
            .version("version_value_test")
            .width(1)
            .height(1)
            .srs("srs_value_test");
    List<GeoServerParameterStringMapValue> detectionGeoServerParameterStringMapValues =
        detectionGeoServerParameterModelMapper.apply(geoServerParameter);
    context.setVariable("detectableObjectModel", new DetectableObjectModel().modelName(TOITURE));
    context.setVariable(
        "geoServerParameterStringMapValues", detectionGeoServerParameterStringMapValues);
    context.setVariable("geoServerUrl", "geo_server_value_test");
    context.setVariable(
        "community", communityAuthorizationRepositoryMock.findById("communityId").get());
    context.setVariable("detection", Detection.builder().id("someId").endToEndId("e2Id").build());
    context.setVariable("shapeFileUrl", null);
    context.setVariable("excelFileUrl", "http://localhost");

    var actual = htmlTemplateParser.apply(DETECTION_SAVED_TEMPLATE, context);

    var expected = expectedEmailBody();
    assertEquals(expected, actual);
  }

  private String expectedEmailBody() {
    return """
<html lang="fr">
<head>
    <title>Detection saved</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #F1E4E7;
            color: #582d37;
            margin: 0;
            padding: 20px;
        }

        section {
            background-color: white;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
            padding: 20px;
            max-width: 600px;
            margin: auto;
            border-left: 6px solid rgba(171, 0, 86, 0.2);
        }

        p {
            font-size: 16px;
            line-height: 1.6;
            color: #660033;
        }

        ul {
            list-style-type: none;
            padding: 0;
        }

        ul li {
            background-color: rgba(0, 0, 0, 0.05);
            margin: 10px 0;
            padding: 10px;
            border-left: 4px solid rgba(171, 0, 86, 0.5);
            border-radius: 4px;
        }

        ul li span {
            font-weight: bold;
            color: rgba(122, 0, 61, 0.7);
        }

        ul li:not(:last-child) {
            margin-bottom: 10px;
        }

        h1 {
            font-size: 24px;
            color: rgba(171, 0, 86, 0.7);
            text-align: center;
        }
    </style>
</head>
<body>
<section>
    <h1>Informations de la détection ID = <span>someId</span> | e2Id = <span>e2Id</span></h1>
    <p>Bonjour,</p>
    <p>Voici les éléments fournis par le consommateur d'API <span>
            <span>communityName</span> ayant comme email <span>communityEmail</span>
            et ID <span>communityId</span>
        </span> :
    </p>
    <ul>
        <li>Email associé : <span></span></li>
        <li>Nom de zone fournie par le consommateur : <span></span></li>
        <li>Model utilisé pour la détection : <span>BP_TOITURE</span></li>
        <li>A besoin d'images : <span>false</span></li>
        <li>Format ZIP demandé : <span>false</span></li>
        <li>Configuration du geoServer :
            <ul>
                <li>geoServerUrl: <span>geo_server_value_test</span></li>
                <li>
                    <span class="indent">service</span> :
                    <span class="indent">service_value_test</span>
                </li>
                <li>
                    <span class="indent">request</span> :
                    <span class="indent">request_value_test</span>
                </li>
                <li>
                    <span class="indent">layers</span> :
                    <span class="indent">layers_value_test</span>
                </li>
                <li>
                    <span class="indent">styles</span> :
                    <span class="indent">styles_value_test</span>
                </li>
                <li>
                    <span class="indent">format</span> :
                    <span class="indent">format_value_test</span>
                </li>
                <li>
                    <span class="indent">transparent</span> :
                    <span class="indent">true</span>
                </li>
                <li>
                    <span class="indent">version</span> :
                    <span class="indent">version_value_test</span>
                </li>
                <li>
                    <span class="indent">width</span> :
                    <span class="indent">1</span>
                </li>
                <li>
                    <span class="indent">height</span> :
                    <span class="indent">1</span>
                </li>
                <li>
                    <span class="indent">srs</span> :
                    <span class="indent">srs_value_test</span>
                </li>
            </ul>
        </li>
        <li>Zone en geoJson fournie :
           \s
            <span>
                non définie
            </span>
        </li>
        <li>Zone à détecter :
           \s
            <span>
                non définie
            </span>

        </li>
        <li>Fichier shape à convertir en geoJson :
           \s
            <span> non définie</span>
        </li>
        <li>Fichier excel à convertir en geoJson :
           \s
            <span> non définie</span>
        </li>
    </ul>
    <p>Cordialement.</p>
    <p>L'équipe BirdIA.</p>
</section>
</body>
</html>
""";
  }
}
