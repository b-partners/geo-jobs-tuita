package app.bpartners.geojobs.repository.model.detection;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum RoofCoveringType {
  ROOF_ARDOISE,
  ROOF_ASPHALTE_BITUME,
  ROOF_BAC_ACIER,
  ROOF_BETON_BRUT,
  ROOF_FIBRO_CIMENT,
  ROOF_GRAVIER,
  ROOF_MEMBRANE_SYNTHETIQUE,
  ROOF_TOLE_ONDULEE,
  ROOF_TUILES,
  ROOF_ZINC;

  // Used for mapping
  @JsonCreator
  public static RoofCoveringType fromString(String value) {
    return Arrays.stream(values())
        .filter(e -> e.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown value: " + value));
  }
}
