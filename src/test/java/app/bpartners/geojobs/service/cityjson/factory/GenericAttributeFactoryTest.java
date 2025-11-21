package app.bpartners.geojobs.service.cityjson.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import org.citygml4j.core.model.core.AbstractGenericAttributeProperty;
import org.citygml4j.core.model.generics.DoubleAttribute;
import org.citygml4j.core.model.generics.IntAttribute;
import org.citygml4j.core.model.generics.StringAttribute;
import org.junit.jupiter.api.Test;

class GenericAttributeFactoryTest {
  @Test
  void make_generic_attribute_ok() {
    var expectedList =
        List.of(
            new AbstractGenericAttributeProperty(new StringAttribute("string", "string")),
            new AbstractGenericAttributeProperty(new DoubleAttribute("double", 2d)),
            new AbstractGenericAttributeProperty(new IntAttribute("int", 2)),
            new AbstractGenericAttributeProperty(
                new StringAttribute("unknown", String.valueOf(List.of(2)))));

    var payload = new LinkedHashMap<String, Object>();
    payload.put("string", "string");
    payload.put("double", 2d);
    payload.put("int", 2);
    payload.put("unknown", String.valueOf(List.of(2)));

    var actualList = GenericAttributeFactory.make(payload);
    for (int i = 0; i < expectedList.size(); i++) {
      var actual = actualList.get(i);
      var expected = expectedList.get(i);

      assertEquals(expected.getObject().getName(), actual.getObject().getName());
      assertEquals(expected.getObject().getValue(), actual.getObject().getValue());
    }
  }
}
