package app.bpartners.geojobs.repository.model.detection;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

@Converter(autoApply = true)
public class RoofCoveringTypeConverter implements AttributeConverter<RoofCoveringType, String> {
  @Override
  public String convertToDatabaseColumn(RoofCoveringType attribute) {
    return attribute == null ? null : attribute.name();
  }

  @Override
  public RoofCoveringType convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return Arrays.stream(RoofCoveringType.values())
        .filter(e -> e.name().equalsIgnoreCase(dbData))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown RoofCoveringType: " + dbData));
  }
}
