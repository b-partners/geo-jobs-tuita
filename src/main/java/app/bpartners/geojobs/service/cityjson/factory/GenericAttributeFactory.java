package app.bpartners.geojobs.service.cityjson.factory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.AbstractGenericAttributeProperty;
import org.citygml4j.core.model.generics.DoubleAttribute;
import org.citygml4j.core.model.generics.IntAttribute;
import org.citygml4j.core.model.generics.StringAttribute;

@Slf4j
public class GenericAttributeFactory {
  private GenericAttributeFactory() {}

  public static List<AbstractGenericAttributeProperty> make(Map<String, Object> properties) {
    return properties.entrySet().stream()
        .map(entry -> makeGenericAttribute(entry.getKey(), entry.getValue()))
        .filter(Objects::nonNull)
        .map(AbstractGenericAttributeProperty::new)
        .toList();
  }

  private static AbstractGenericAttribute<?> makeGenericAttribute(String key, Object value) {
    return switch (value) {
      case Double doubleValue -> new DoubleAttribute(key, doubleValue);
      case Integer intValue -> new IntAttribute(key, intValue);
      case String stringValue -> new StringAttribute(key, stringValue);
      default -> {
        log.error("Unsupported attribute type: {}", value.getClass());
        yield new StringAttribute(key, String.valueOf(value));
      }
    };
  }
}
