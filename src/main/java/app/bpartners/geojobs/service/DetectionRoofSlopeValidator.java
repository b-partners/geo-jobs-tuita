package app.bpartners.geojobs.service;

import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class DetectionRoofSlopeValidator implements Consumer<Detection> {
  @Override
  public void accept(Detection detection) {
    var detectionIdentifier = detection.getId();
    if (!detection.hasToitureModelName()) {
      throw new IllegalArgumentException(
          "Only BP_TOITURE model handle roof slope and height computing, "
              + "otherwise Detection.id "
              + detectionIdentifier
              + " has "
              + detection.getDetectableObjectModel().getModelName());
    }
    if (detection.getFeatureWithDelimitations() == null
        || detection.getFeatureWithDelimitations().isEmpty()) {
      throw new IllegalArgumentException(
          "Roofs not retrieved yet for Detection.id " + detectionIdentifier);
    }
  }
}
