package app.bpartners.geojobs.endpoint.event.model.zone;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_2;

import app.bpartners.geojobs.endpoint.event.EventStack;
import app.bpartners.geojobs.endpoint.event.model.PojaEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import javax.annotation.processing.Generated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Generated("EventBridge")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode
@ToString
public class ZoneDetectionJobSucceeded extends PojaEvent {
  @JsonProperty("succeededJobId")
  private String succeededJobId;

  @Override
  public Duration maxConsumerDuration() {
    return Duration.ofMinutes(1);
  }

  @Override
  public Duration maxConsumerBackoffBetweenRetries() {
    return Duration.ofMinutes(1);
  }

  @Override
  public EventStack getEventStack() {
    return EVENT_STACK_2;
  }
}
