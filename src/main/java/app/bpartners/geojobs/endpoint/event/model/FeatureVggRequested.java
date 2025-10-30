package app.bpartners.geojobs.endpoint.event.model;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_4;

import app.bpartners.geojobs.endpoint.event.EventStack;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import java.time.Duration;
import lombok.*;

@NoArgsConstructor
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode(callSuper = false)
@ToString
public class FeatureVggRequested extends PojaEvent {
  private String detectionIdentifier;
  private Feature feature;
  private int featureNb = 0;

  public FeatureVggRequested(String detectionIdentifier, Feature feature, int featureNb) {
    this.detectionIdentifier = detectionIdentifier;
    this.feature = feature;
    this.featureNb = featureNb;
  }

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
    return EVENT_STACK_4;
  }
}
