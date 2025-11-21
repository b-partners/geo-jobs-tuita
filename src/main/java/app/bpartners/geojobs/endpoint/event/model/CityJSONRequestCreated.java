package app.bpartners.geojobs.endpoint.event.model;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_4;

import app.bpartners.geojobs.endpoint.event.EventStack;
import java.time.Duration;
import lombok.*;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@Builder(toBuilder = true)
@NoArgsConstructor
public class CityJSONRequestCreated extends PojaEvent {
  private String requestId;

  @Override
  public Duration maxConsumerDuration() {
    return Duration.ofMinutes(5);
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
