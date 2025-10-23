package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.ModelName.*;

import app.bpartners.geojobs.endpoint.rest.model.ModelName;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionSupportedModelValidator implements Consumer<Detection> {
  public static final List<ModelName> UNSUPPORTED_MODELS =
      List.of(VEGETATION, VOIRIE_TROTTOIRS, TAMPONS, SIGN, CIMETIERE, OLD, CYCL);

  @Override
  public void accept(Detection detection) {
    if (detection.getDetectableObjectModel() == null
        || detection.getDetectableObjectModel().getModelName() == null) {
      return;
    }
    var detectionModelName = detection.getDetectableObjectModel().getModelName();
    if (UNSUPPORTED_MODELS.contains(detectionModelName)) {
      throw new UnsupportedOperationException(
          "Detection has unsupported model " + detectionModelName);
    }
  }
}
