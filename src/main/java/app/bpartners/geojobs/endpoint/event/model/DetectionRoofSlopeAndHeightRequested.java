package app.bpartners.geojobs.endpoint.event.model;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_4;

import app.bpartners.geojobs.endpoint.event.EventStack;
import java.time.Duration;
import lombok.*;

@Data
@ToString
@Builder(toBuilder = true)
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class DetectionRoofSlopeAndHeightRequested extends PojaEvent {
  private String detectionId;

  @Override
  public Duration maxConsumerDuration() {
    return Duration.ofMinutes(8);
  }

  @Override
  public Duration maxConsumerBackoffBetweenRetries() {
    return Duration.ofMinutes(2);
  }

  @Override
  public EventStack getEventStack() {
    return EVENT_STACK_4;
  }
}
