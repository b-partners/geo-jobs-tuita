package app.bpartners.geojobs.service.cityjson.factory;

import app.bpartners.geojobs.service.cityjson.model.BuildingData;
import java.util.List;
import org.citygml4j.core.model.core.AbstractCityObjectProperty;
import org.citygml4j.core.model.core.CityModel;

public class CityModelFactory {
  private CityModelFactory() {}

  public static CityModel make(List<BuildingData> buildingData) {
    var cityObjects =
        buildingData.stream()
            .map(BuildingFactory::make)
            .map(AbstractCityObjectProperty::new)
            .toList();

    var cityModel = new CityModel();
    cityModel.setCityObjectMembers(cityObjects);

    return cityModel;
  }
}
