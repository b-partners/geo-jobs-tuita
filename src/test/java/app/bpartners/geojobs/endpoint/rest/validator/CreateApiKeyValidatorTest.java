package app.bpartners.geojobs.endpoint.rest.validator;

import static app.bpartners.geojobs.endpoint.rest.model.CreateApiKey.ConsumerTypeEnum.INSURANCE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.model.CreateApiKey;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.model.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class CreateApiKeyValidatorTest {
  CreateApiKeyValidator subject = new CreateApiKeyValidator();

  @Test
  void accept_without_exception() {
    var createApiKeyMock = mock(CreateApiKey.class);
    var detectableObjectModelMock = mock(DetectableObjectModel.class);
    when(createApiKeyMock.getConsumerEmail()).thenReturn("dummyConsumerEmail");
    when(createApiKeyMock.getConsumerName()).thenReturn("dummyConsumerName");
    when(createApiKeyMock.getConsumerType()).thenReturn(INSURANCE);
    when(createApiKeyMock.getDetectableObjectModel()).thenReturn(detectableObjectModelMock);
    when(detectableObjectModelMock.getModelName()).thenReturn(TOITURE);

    assertDoesNotThrow(() -> subject.accept(createApiKeyMock));
  }

  @Test
  void throws_mandatory_attribute_missing_exception() {
    var createApiKeyMock = mock(CreateApiKey.class);
    var detectableObjectModelMock = mock(DetectableObjectModel.class);
    when(createApiKeyMock.getConsumerEmail()).thenReturn(null);
    when(createApiKeyMock.getConsumerName()).thenReturn(null);
    when(createApiKeyMock.getConsumerType()).thenReturn(null);
    when(createApiKeyMock.getDetectableObjectModel()).thenReturn(detectableObjectModelMock);
    when(detectableObjectModelMock.getModelName()).thenReturn(null);

    var actualException =
        assertThrows(BadRequestException.class, () -> subject.accept(createApiKeyMock));

    assertEquals(
        "CreateApiKey.consumerName is mandatory. "
            + "CreateApiKey.consumerEmail is mandatory."
            + " CreateApiKey.detectableObjectModel.modelName is mandatory."
            + " CreateApiKey.consumerType is mandatory.",
        actualException.getMessage());
  }

  @Test
  void throws_mandatory_detectable_object_model_attribute_missing_exception() {
    var createApiKeyMock = mock(CreateApiKey.class);
    when(createApiKeyMock.getConsumerEmail()).thenReturn("dummyConsumerEmail");
    when(createApiKeyMock.getConsumerName()).thenReturn("dummyConsumerName");
    when(createApiKeyMock.getConsumerType()).thenReturn(INSURANCE);
    when(createApiKeyMock.getDetectableObjectModel()).thenReturn(null);
    when(createApiKeyMock.getAllowedModels()).thenReturn(null);

    var actualException =
        assertThrows(BadRequestException.class, () -> subject.accept(createApiKeyMock));

    assertEquals(
        "Either CreateApiKey.detectableObjectModel is mandatory or CreateApiKey.allowedModels is"
            + " mandatory. ",
        actualException.getMessage());
  }
}
