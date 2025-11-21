package app.bpartners.geojobs.endpoint.event.model;

import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@ToString
public class DetectionAddressConversionTaskCreated
    extends TaskCreated<DetectionAddressConversionTask> {
  public DetectionAddressConversionTaskCreated(DetectionAddressConversionTask task) {
    super(task);
  }
}
