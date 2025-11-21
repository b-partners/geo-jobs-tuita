package app.bpartners.geojobs.service;

import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import app.bpartners.geojobs.service.dashboard.AreaPictureApi;
import app.bpartners.geojobs.service.dashboard.mapper.AreaPictureDetailsMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DetectionAddressConversionTaskConsumer
    implements TaskConsumer<DetectionAddressConversionTask> {
  private final AreaPictureApi areaPictureApi;
  private final AreaPictureDetailsMapper areaPictureDetailsMapper;
  private final String adminApiKey;

  public DetectionAddressConversionTaskConsumer(
      AreaPictureApi areaPictureApi,
      AreaPictureDetailsMapper areaPictureDetailsMapper,
      @Value("${admin.api.key}") String adminApiKey) {
    this.areaPictureApi = areaPictureApi;
    this.areaPictureDetailsMapper = areaPictureDetailsMapper;
    this.adminApiKey = adminApiKey;
  }

  @Override
  public void accept(DetectionAddressConversionTask task) {
    var address = task.getAddress();
    var crupdateAreaPictureDetails = areaPictureDetailsMapper.toCrupdateAreaPictureDetails(address);

    var areaPictureId = randomUUID().toString();
    var areaPictureDetails =
        areaPictureApi.crupdateAreaPictureDetails(
            areaPictureId, crupdateAreaPictureDetails, adminApiKey);

    var feature = areaPictureDetailsMapper.toFeature(areaPictureDetails, address);
    task.setFeature(feature);
    task.setLayer(areaPictureDetails.actualLayer().name());
  }
}
