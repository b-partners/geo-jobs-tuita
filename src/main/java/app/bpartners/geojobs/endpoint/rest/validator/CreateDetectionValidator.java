package app.bpartners.geojobs.endpoint.rest.validator;

import app.bpartners.geojobs.endpoint.rest.model.CreateDetection;
import app.bpartners.geojobs.model.exception.BadRequestException;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class CreateDetectionValidator implements Consumer<CreateDetection> {
  @Override
  public void accept(CreateDetection createDetection) {
    StringBuilder exceptionMessageBuilder = new StringBuilder();
    if (createDetection.getEmailReceiver() == null
        || createDetection.getEmailReceiver().isEmpty()) {
      exceptionMessageBuilder.append("CreateDetection.emailReceiver is mandatory. ");
    }
    if (createDetection.getZoneName() == null || createDetection.getZoneName().isEmpty()) {
      exceptionMessageBuilder.append("CreateDetection.zoneName is mandatory. ");
    }
    if (createDetection.getDetectableObjectModel() == null
        || createDetection.getDetectableObjectModel().getModelName() == null) {
      exceptionMessageBuilder.append("CreateDetection.detectableObjectModel is mandatory.");
    }
    var exceptionMessage = exceptionMessageBuilder.toString();
    if (!exceptionMessage.isEmpty()) {
      throw new BadRequestException(exceptionMessage);
    }
  }
}
