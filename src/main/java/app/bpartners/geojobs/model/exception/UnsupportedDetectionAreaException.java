package app.bpartners.geojobs.model.exception;

import lombok.Getter;

public class UnsupportedDetectionAreaException extends RuntimeException {
  @Getter private final Double computedArea;

  public UnsupportedDetectionAreaException(String message, Double computedArea) {
    super(message);
    this.computedArea = computedArea;
  }
}
