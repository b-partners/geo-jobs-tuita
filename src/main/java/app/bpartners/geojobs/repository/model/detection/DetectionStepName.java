package app.bpartners.geojobs.repository.model.detection;

import lombok.Getter;

@Getter
public enum DetectionStepName {
  CONFIGURING("CONFIGURING"),

  TILING("TILING"),

  MACHINE_DETECTION("MACHINE_DETECTION"),

  HUMAN_DETECTION("HUMAN_DETECTION"),

  GEO_JSON_CONVERSION("GEO_JSON_CONVERSION");

  private final String value;

  DetectionStepName(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  public static DetectionStepName fromValue(String value) {
    for (DetectionStepName b : DetectionStepName.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
