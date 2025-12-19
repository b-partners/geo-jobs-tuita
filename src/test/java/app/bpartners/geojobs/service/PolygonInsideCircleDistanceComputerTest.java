package app.bpartners.geojobs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class PolygonInsideCircleDistanceComputerTest {
  PolygonInsideCircleDistanceComputer subject = new PolygonInsideCircleDistanceComputer();
  GeometryConverter geometryConverter = new GeometryConverter(null);

  @Test
  void retrieve_about_10_meters() {
    var polygonToCheck =
        geometryConverter.readGeometryFromString(
            "{ \"coordinates\": [ [ [ 1.5248518203985384, 43.53318856764645 ], ["
                + " 1.5247464662252241, 43.53307892644253 ], [ 1.5248993997022922,"
                + " 43.53301733017361 ], [ 1.5249911597872767, 43.53312450764125 ], ["
                + " 1.5248518203985384, 43.53318856764645 ] ] ], \"type\": \"Polygon\" }",
            16);

    var actual = computeActualCircleRadius(polygonToCheck);

    assertEquals(10, actual.intValue());
  }

  @Test
  void retrieve_about_100_meters() {
    var polygonToCheck =
        geometryConverter.readGeometryFromString(
            "{\n"
                + "        \"coordinates\": [\n"
                + "          [\n"
                + "            [\n"
                + "              1.5276019183529002,\n"
                + "              43.530410705845355\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5276019183529002,\n"
                + "              43.529076115408174\n"
                + "            ],\n"
                + "            [\n"
                + "              1.529568769986298,\n"
                + "              43.529076115408174\n"
                + "            ],\n"
                + "            [\n"
                + "              1.529568769986298,\n"
                + "              43.530410705845355\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5276019183529002,\n"
                + "              43.530410705845355\n"
                + "            ]\n"
                + "          ]\n"
                + "        ],\n"
                + "        \"type\": \"Polygon\"\n"
                + "      }",
            16);

    var actual = computeActualCircleRadius(polygonToCheck);

    assertEquals(108, actual.intValue());
  }

  @Test
  void retrieve_about_1000_meters() {
    var polygonToCheck =
        geometryConverter.readGeometryFromString(
            "{\n"
                + "        \"coordinates\": [[\n"
                + "          [\n"
                + "            [\n"
                + "              1.5347181401927799,\n"
                + "              43.533062580912315\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5347181401927799,\n"
                + "              43.52500728659851\n"
                + "            ],\n"
                + "            [\n"
                + "              1.551504973513289,\n"
                + "              43.52500728659851\n"
                + "            ],\n"
                + "            [\n"
                + "              1.551504973513289,\n"
                + "              43.533062580912315\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5347181401927799,\n"
                + "              43.533062580912315\n"
                + "            ]\n"
                + "          ]\n"
                + "        ],"
                + "[\n"
                + "          [\n"
                + "            [\n"
                + "              1.5428576361837827,\n"
                + "              43.54155428778387\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5428576361837827,\n"
                + "              43.53779457737181\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5549063685101032,\n"
                + "              43.53779457737181\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5549063685101032,\n"
                + "              43.54155428778387\n"
                + "            ],\n"
                + "            [\n"
                + "              1.5428576361837827,\n"
                + "              43.54155428778387\n"
                + "            ]\n"
                + "          ]\n"
                + "        ]]"
                + ",\n"
                + "        \"type\": \"MultiPolygon\"\n"
                + "      }",
            16);

    var actual = computeActualCircleRadius(polygonToCheck);

    assertEquals(1089, actual.intValue());
  }

  private Double computeActualCircleRadius(Geometry polygonToCheck) {
    var centerLongitude = polygonToCheck.getCentroid().getCoordinate().getX();
    var centerLatitude = polygonToCheck.getCentroid().getCoordinate().getY();
    var multiPolygonCoordinates =
        geometryConverter.geometryToMultiPolygonCoordinates(polygonToCheck);

    return subject.apply(
        List.of(BigDecimal.valueOf(centerLongitude), BigDecimal.valueOf(centerLatitude)),
        multiPolygonCoordinates.getFirst().getFirst());
  }
}
