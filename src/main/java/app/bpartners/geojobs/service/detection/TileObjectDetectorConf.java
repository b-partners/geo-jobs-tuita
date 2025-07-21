package app.bpartners.geojobs.service.detection;

import app.bpartners.geojobs.store.ParameterStoreConf;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

@Component
@RequiredArgsConstructor
public class TileObjectDetectorConf {
  private final String env = System.getenv("ENV");
  private final ParameterStoreConf parameterStoreConf;

  public String getTileDetectionApiUrls() {
    var parameterName = String.format("/geo-jobs/%s/tiles/detection/urls", env);
    var parameterRequest = GetParameterRequest.builder().name(parameterName).build();
    var parameterResponse = parameterStoreConf.getSsmClient().getParameter(parameterRequest);
    return parameterResponse.parameter().value();
  }
}
