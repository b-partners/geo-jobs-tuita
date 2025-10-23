package app.bpartners.geojobs.endpoint.event.model;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_1;
import static java.lang.Math.random;

import app.bpartners.geojobs.PojaGenerated;
import app.bpartners.geojobs.endpoint.event.EventStack;
import java.io.Serializable;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

@PojaGenerated
@SuppressWarnings("all")
public abstract class PojaEvent implements Serializable {

  @Getter @Setter protected int attemptNb;

  public abstract Duration maxConsumerDuration();

  public Duration eventHandlerInitMaxDuration() {
    return Duration.ofSeconds(90); // note(init-visibility)
  }

  private Duration randomConsumerBackoffBetweenRetries() {
    return Duration.ofSeconds((int) (random() * maxConsumerBackoffBetweenRetries().toSeconds()));
  }

  public abstract Duration maxConsumerBackoffBetweenRetries();

  public final Duration randomVisibilityTimeout() {
    return Duration.ofSeconds(
        eventHandlerInitMaxDuration().toSeconds()
            + maxConsumerDuration().toSeconds()
            + randomConsumerBackoffBetweenRetries().toSeconds());
  }

  public EventStack getEventStack() {
    return EVENT_STACK_1;
  }

  public String getEventSource() {
    var eventStack = getEventStack();
    switch (eventStack) {
      case EVENT_STACK_1 -> {
        return "app.bpartners.geojobs.event1";
      }
      case EVENT_STACK_2 -> {
        return "app.bpartners.geojobs.event2";
      }
      case EVENT_STACK_3 -> {
        return "app.bpartners.geojobs.event3";
      }
      case EVENT_STACK_4 -> {
        return "app.bpartners.geojobs.event4";
      }
    }
    throw new IllegalStateException("Unknown event stack " + eventStack);
  }
}
