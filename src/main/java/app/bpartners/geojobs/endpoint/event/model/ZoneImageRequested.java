package app.bpartners.geojobs.endpoint.event.model;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_2;

import app.bpartners.geojobs.endpoint.event.EventStack;
import java.time.Duration;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode(callSuper = false)
@ToString
public class ZoneImageRequested extends PojaEvent {
  private String detectionIdentifier;

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
