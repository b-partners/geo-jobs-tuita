package app.bpartners.geojobs.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectionRoofSlopeValidatorTest {

  DetectionRoofSlopeValidator subject = new DetectionRoofSlopeValidator();

  @Test
  void do_not_throws() {
    var detectionMock = mock(Detection.class);
    var featureWithDelimitationMock = mock(FeatureWithDelimitation.class);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(List.of(featureWithDelimitationMock));

    assertDoesNotThrow(() -> subject.accept(detectionMock));
  }

  @Test
  void throws_when_not_toiture_model() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.hasToitureModelName()).thenReturn(false);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(null));

    var actual = assertThrows(IllegalArgumentException.class, () -> subject.accept(detectionMock));

    var expectedExceptionMessage =
        "Only BP_TOITURE model handle roof slope and height computing, otherwise Detection.id "
            + detectionIdentifier
            + " has null";
    assertEquals(expectedExceptionMessage, actual.getMessage());
  }

  @Test
  void throws_when_no_roofs_retrieved() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getFeatureWithDelimitations()).thenReturn(null);
    var detectionIdentifierTwo = randomUUID().toString();
    var detectionTwoMock = mock(Detection.class);
    when(detectionTwoMock.getId()).thenReturn(detectionIdentifierTwo);
    when(detectionTwoMock.hasToitureModelName()).thenReturn(true);
    when(detectionTwoMock.getFeatureWithDelimitations()).thenReturn(List.of());

    var actual = assertThrows(IllegalArgumentException.class, () -> subject.accept(detectionMock));
    var actualCaseTwo =
        assertThrows(IllegalArgumentException.class, () -> subject.accept(detectionTwoMock));

    var expectedExceptionMessage = "Roofs not retrieved yet for Detection.id ";
    assertEquals(expectedExceptionMessage + detectionIdentifier, actual.getMessage());
    assertEquals(expectedExceptionMessage + detectionIdentifierTwo, actualCaseTwo.getMessage());
  }
}
