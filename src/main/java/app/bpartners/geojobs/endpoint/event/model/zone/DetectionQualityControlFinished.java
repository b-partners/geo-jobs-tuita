package app.bpartners.geojobs.endpoint.event.model.zone;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_2;

import app.bpartners.geojobs.endpoint.event.EventStack;
import app.bpartners.geojobs.endpoint.event.model.PojaEvent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode(callSuper = false)
@ToString
public class DetectionQualityControlFinished extends PojaEvent {
  private Detection detection;

  @Override
  public Duration maxConsumerDuration() {
    return Duration.ofSeconds(30L);
  }

  @Override
  public Duration maxConsumerBackoffBetweenRetries() {
    return Duration.ofSeconds(30L);
  }

  @Override
  public EventStack getEventStack() {
    return EVENT_STACK_2;
  }
}
