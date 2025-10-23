package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.*;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.HUMIDITE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.MOISISSURE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.USURE;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DetectableObjectTypeMapper {

  public static final double DEFAULT_CONFIDENCE = 1.0;

  public DetectableType toDomain(DetectableObjectType rest) {
    return switch (rest) {
      case TOITURE_REVETEMENT -> DetectableType.TOITURE_REVETEMENT;
      case PANNEAU_PHOTOVOLTAIQUE -> DetectableType.PANNEAU_PHOTOVOLTAIQUE;
      case PISCINE -> DetectableType.PISCINE;
      case PASSAGE_PIETON -> DetectableType.PASSAGE_PIETON;
      case ARBRE -> DetectableType.ARBRE;
      case ARBRE_INDIVIDUALISE -> DetectableType.ARBRE_INDIVIDUALISE;
      case CANOPE -> DetectableType.CANOPE;
      case TROTTOIR -> DetectableType.TROTTOIR;
      case LINE -> DetectableType.LINE;
      case ESPACE_ARBORE -> DetectableType.ESPACE_ARBORE;
      case ESPACE_VERT -> DetectableType.ESPACE_VERT;
      case VOIE_CARROSSABLE -> DetectableType.VOIE_CARROSSABLE;
      case PARKING -> DetectableType.PARKING;
      case MOISISSURE_COULEUR -> DetectableType.MOISISSURE_COULEUR;
      case MOISISSURE_CLAIR -> DetectableType.MOISISSURE_CLAIR;
      case MOISISSURE_NOIRCIE -> DetectableType.MOISISSURE_NOIRCIE;
      case MOISISSURE -> DetectableType.MOISISSURE;
      case USURE_LEGER -> DetectableType.USURE_LEGER;
      case USURE_IMPORTANTE -> DetectableType.USURE_IMPORTANTE;
      case USURE -> DetectableType.USURE;
      case FISSURE_CASSURE -> DetectableType.FISSURE_CASSURE;
      case OBSTACLE -> DetectableType.OBSTACLE;
      case CHEMINEE -> DetectableType.CHEMINEE;
      case HUMIDITE_CLAIR -> DetectableType.HUMIDITE_CLAIR;
      case HUMIDITE_INTENSE -> DetectableType.HUMIDITE_INTENSE;
      case HUMIDITE -> DetectableType.HUMIDITE;
      case RISQUE_FEU -> DetectableType.RISQUE_FEU;
      case VELUX -> DetectableType.VELUX;
      case BATI_TUILES -> DetectableType.BATI_TUILES;
      case BATI_BETON -> DetectableType.BATI_BETON;
      case BATI_ARDOISE -> DetectableType.BATI_ARDOISE;
      case BATI_AUTRES -> DetectableType.BATI_AUTRES;
      case BATI -> DetectableType.BATI;
      case ESPACE_VERT_PARKING -> DetectableType.ESPACE_VERT_PARKING;
      case BACKGROUND -> DetectableType.BACKGROUND;
      case ROAD -> DetectableType.ROAD;
      case SURFACES_ARTIFICIALISEES -> DetectableType.SURFACES_ARTIFICIALISEES;
      case SURFACES_PERMEABLES -> DetectableType.SURFACES_PERMEABLES;
      case PISTES_CYCLABLES -> DetectableType.PISTES_CYCLABLES;
      case SYMBOLES_CYCLABLES -> DetectableType.SYMBOLES_CYCLABLES;
      case MARQUAGES_VOIRIES -> DetectableType.MARQUAGES_VOIRIES;
      case CIMETIERE -> DetectableType.CIMETIERE;
      case TOMBE_SIMPLE -> DetectableType.TOMBE_SIMPLE;
      case TOMBE_DOUBLE -> DetectableType.TOMBE_DOUBLE;
      case TOMBE_NON_GEOMETRIQUE -> DetectableType.TOMBE_NON_GEOMETRIQUE;
    };
  }

  public DetectableObjectType toRest(DetectableType domain) {
    return switch (domain) {
      case PISCINE -> PISCINE;
      case ROAD -> ROAD;
      case TOITURE_REVETEMENT -> TOITURE_REVETEMENT;
      case ARBRE -> ARBRE;
      case PASSAGE_PIETON -> PASSAGE_PIETON;
      case PANNEAU_PHOTOVOLTAIQUE -> PANNEAU_PHOTOVOLTAIQUE;
      case TROTTOIR -> TROTTOIR;
      case LINE -> LINE;
      case ESPACE_VERT -> ESPACE_VERT;
      case VOIE_CARROSSABLE -> VOIE_CARROSSABLE;
      case MOISISSURE_CLAIR -> MOISISSURE_CLAIR;
      case MOISISSURE_COULEUR -> MOISISSURE_COULEUR;
      case MOISISSURE_NOIRCIE -> MOISISSURE_NOIRCIE;
      case USURE_IMPORTANTE -> USURE_IMPORTANTE;
      case MOISISSURE -> MOISISSURE;
      case USURE_LEGER -> USURE_LEGER;
      case USURE -> USURE;
      case FISSURE_CASSURE -> FISSURE_CASSURE;
      case OBSTACLE -> OBSTACLE;
      case CHEMINEE -> CHEMINEE;
      case HUMIDITE_CLAIR -> HUMIDITE_CLAIR;
      case HUMIDITE_INTENSE -> HUMIDITE_INTENSE;
      case HUMIDITE -> HUMIDITE;
      case RISQUE_FEU -> RISQUE_FEU;
      case VELUX -> VELUX;
      case BATI_TUILES -> BATI_TUILES;
      case PARKING -> PARKING;
      case BATI_BETON -> BATI_BETON;
      case BATI_AUTRES -> BATI_AUTRES;
      case BATI_ARDOISE -> BATI_ARDOISE;
      case TOMBE -> null;
      case ESPACE_VERT_PARKING -> ESPACE_VERT_PARKING;
      case BACKGROUND -> BACKGROUND;
      case ARBRE_INDIVIDUALISE -> ARBRE_INDIVIDUALISE;
      case CANOPE -> CANOPE;
      case ESPACE_ARBORE -> ESPACE_ARBORE;
      case BATI -> BATI;
      case SURFACES_ARTIFICIALISEES -> SURFACES_ARTIFICIALISEES;
      case SURFACES_PERMEABLES -> SURFACES_PERMEABLES;
      case PISTES_CYCLABLES -> PISTES_CYCLABLES;
      case SYMBOLES_CYCLABLES -> SYMBOLES_CYCLABLES;
      case MARQUAGES_VOIRIES -> MARQUAGES_VOIRIES;
      case CIMETIERE -> CIMETIERE;
      case TOMBE_SIMPLE -> TOMBE_SIMPLE;
      case TOMBE_DOUBLE -> TOMBE_DOUBLE;
      case TOMBE_NON_GEOMETRIQUE -> TOMBE_NON_GEOMETRIQUE;
    };
  }

  public List<DetectableObjectType> mapFromModel(DetectableObjectModel model) {
    var modelName = model.getModelName();
    return modelName == null ? List.of() : mapFromModel(modelName);
  }

  public List<DetectableObjectType> mapFromModel(ModelName modelName) {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    switch (modelName) {
      case TOITURE -> objectTypes.addAll(detectableObjectTypeForToitureModel());
      case LOM -> objectTypes.addAll(detectableObjectTypeForLomModel());
      case ZAN -> objectTypes.addAll(detectableObjectTypeForZanModel());
      case CLIMAT_RESILIENCE -> objectTypes.addAll(detectableObjectTypeForClimatResilienceModel());
      case CONFIRMITE_PLU -> objectTypes.addAll(detectableObjectTypeForConformitePluModel());
      case VOIRIE_TROTTOIRS -> objectTypes.addAll(detectableObjectTypeForVoirieTrottoirsModel());
      case OLD -> objectTypes.addAll(detectableObjectTypeForOldModel());
      case CYCL -> objectTypes.addAll(detectableObjectTypeForCyclModel());
      case SIGN -> objectTypes.addAll(detectableObjectTypeForSignModel());
      case VEGETATION -> objectTypes.addAll(detectableObjectTypeForVegetationModel());
      case TAMPONS -> objectTypes.addAll(detectableObjectTypeForTamponsModel());
      case CIMETIERE -> objectTypes.addAll(detectableObjectTypeForCimetiereModel());
      case STATIONNEMENT -> objectTypes.addAll(detectableObjectTypeForStationnementModel());
    }
    return objectTypes;
  }

  public static List<DetectableObjectType> detectableObjectTypeForToitureModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(TOITURE_REVETEMENT);
    // objectTypes.add(ARBRE);
    objectTypes.add(PANNEAU_PHOTOVOLTAIQUE);
    objectTypes.add(MOISISSURE_NOIRCIE);
    objectTypes.add(MOISISSURE_CLAIR);
    objectTypes.add(MOISISSURE_COULEUR);
    objectTypes.add(MOISISSURE);
    objectTypes.add(USURE_IMPORTANTE);
    objectTypes.add(USURE_LEGER);
    objectTypes.add(USURE);
    // objectTypes.add(FISSURE_CASSURE);
    objectTypes.add(OBSTACLE);
    objectTypes.add(CHEMINEE);
    objectTypes.add(HUMIDITE_INTENSE);
    objectTypes.add(HUMIDITE_CLAIR);
    objectTypes.add(HUMIDITE);
    // objectTypes.add(RISQUE_FEU);
    objectTypes.add(VELUX);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForLomModel() {
    return List.of(PASSAGE_PIETON);
  }

  private List<DetectableObjectType> detectableObjectTypeForClimatResilienceModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(PARKING);
    objectTypes.add(PANNEAU_PHOTOVOLTAIQUE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForZanModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(SURFACES_ARTIFICIALISEES);
    objectTypes.add(SURFACES_PERMEABLES);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForConformitePluModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(BATI);
    objectTypes.add(VELUX);
    objectTypes.add(PANNEAU_PHOTOVOLTAIQUE);
    objectTypes.add(PISCINE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForVoirieTrottoirsModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(TROTTOIR);
    objectTypes.add(VOIE_CARROSSABLE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForOldModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(ARBRE);
    objectTypes.add(ESPACE_VERT);
    objectTypes.add(VOIE_CARROSSABLE);
    objectTypes.add(TROTTOIR);
    objectTypes.add(PARKING);
    objectTypes.add(RISQUE_FEU);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForCyclModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(PISTES_CYCLABLES);
    objectTypes.add(SYMBOLES_CYCLABLES);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForSignModel() {
    return List.of(MARQUAGES_VOIRIES);
  }

  private List<DetectableObjectType> detectableObjectTypeForVegetationModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(ESPACE_VERT);
    objectTypes.add(ESPACE_ARBORE);
    objectTypes.add(ARBRE_INDIVIDUALISE);
    objectTypes.add(CANOPE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForTamponsModel() {
    // TODO: not implemented
    return new ArrayList<>();
  }

  private List<DetectableObjectType> detectableObjectTypeForCimetiereModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(ESPACE_VERT);
    objectTypes.add(ESPACE_ARBORE);
    objectTypes.add(CIMETIERE);
    objectTypes.add(TOMBE_SIMPLE);
    objectTypes.add(TOMBE_DOUBLE);
    objectTypes.add(TOMBE_NON_GEOMETRIQUE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForStationnementModel() {
    // TODO: not implemented
    return new ArrayList<>();
  }

  public List<DetectableObjectConfiguration> mapDefaultConfigurationsFromModel(
      String detectionId, ModelName modelName) {
    var objectTypes = mapFromModel(modelName);
    return objectTypes.stream()
        .map(
            detectableObjectType -> {
              var objectType = toDomain(detectableObjectType);
              return DetectableObjectConfiguration.builder()
                  .id(randomUUID().toString())
                  .detectionId(detectionId)
                  .objectType(objectType)
                  .detectionJobId(null)
                  .minConfidenceForDetection(minimumConfidenceForDetection(detectableObjectType))
                  .bucketStorageName(null) // default bucket storage
                  .build();
            })
        .collect(Collectors.toList());
  }

  private Double minimumConfidenceForDetection(DetectableObjectType objectType) {
    switch (objectType) {
      case TROTTOIR -> {
        return 0.252;
      }
      case PISCINE, PANNEAU_PHOTOVOLTAIQUE -> {
        return 0.27;
      }
      case TOITURE_REVETEMENT, LINE, BATI_TUILES, PARKING, BATI_BETON, BATI_AUTRES -> {
        return 0.252;
      }
      case ARBRE -> {
        return 0.2504;
      }
      case PASSAGE_PIETON -> {
        return 0.29;
      }
      case ESPACE_VERT -> {
        return 0.251;
      }
      case BATI_ARDOISE -> {
        return 0.255;
      }
      case VOIE_CARROSSABLE -> {
        return 0.0;
      }
      case MOISISSURE_CLAIR, MOISISSURE_COULEUR, MOISISSURE_NOIRCIE -> {
        return 0.0;
      }
      case USURE_IMPORTANTE, USURE_LEGER -> {
        return 0.0;
      }
      case FISSURE_CASSURE -> {
        return 0.0;
      }
      case OBSTACLE -> {
        return 0.0;
      }
      case CHEMINEE -> {
        return 0.0;
      }
      case HUMIDITE_CLAIR, HUMIDITE_INTENSE -> {
        return 0.0;
      }
      case RISQUE_FEU -> {
        return 0.0;
      }
      case VELUX -> {
        return 0.0;
      }
      case ESPACE_VERT_PARKING -> {
        return 0.0;
      }
      default -> {
        return DEFAULT_CONFIDENCE;
      }
    }
  }
}
