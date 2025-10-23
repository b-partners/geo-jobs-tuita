package app.bpartners.geojobs.endpoint.rest.validator;

import app.bpartners.geojobs.endpoint.rest.model.CreateApiKey;
import app.bpartners.geojobs.model.exception.BadRequestException;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class CreateApiKeyValidator implements Consumer<CreateApiKey> {
  @Override
  public void accept(CreateApiKey createApiKey) {
    var exceptionMessageBuilder = new StringBuilder();
    if (createApiKey.getConsumerName() == null) {
      exceptionMessageBuilder.append("CreateApiKey.consumerName is mandatory. ");
    }
    if (createApiKey.getConsumerEmail() == null) {
      exceptionMessageBuilder.append("CreateApiKey.consumerEmail is mandatory. ");
    }
    if (createApiKey.getDetectableObjectModel() == null && createApiKey.getAllowedModels() == null
        || (createApiKey.getDetectableObjectModel() == null
            && createApiKey.getAllowedModels() != null
            && createApiKey.getAllowedModels().isEmpty())) {
      exceptionMessageBuilder.append(
          "Either CreateApiKey.detectableObjectModel is mandatory or CreateApiKey.allowedModels is"
              + " mandatory. ");
    }
    if (createApiKey.getDetectableObjectModel() != null
        && createApiKey.getDetectableObjectModel().getModelName() == null) {
      exceptionMessageBuilder.append("CreateApiKey.detectableObjectModel.modelName is mandatory. ");
    }
    if (createApiKey.getConsumerType() == null) {
      exceptionMessageBuilder.append("CreateApiKey.consumerType is mandatory.");
    }
    var exceptionMessage = exceptionMessageBuilder.toString();
    if (!exceptionMessage.isEmpty()) {
      throw new BadRequestException(exceptionMessage);
    }
  }
}
