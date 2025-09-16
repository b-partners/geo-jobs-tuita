package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.*;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.HUMIDITE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.MOISISSURE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.USURE;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.model.exception.NotImplementedException;
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
      case PISCINE -> DetectableType.PISCINE;
      case TOITURE_REVETEMENT -> DetectableType.TOITURE_REVETEMENT;
      case ARBRE -> DetectableType.ARBRE;
      case PASSAGE_PIETON -> DetectableType.PASSAGE_PIETON;
      case PANNEAU_PHOTOVOLTAIQUE -> DetectableType.PANNEAU_PHOTOVOLTAIQUE;
      case TROTTOIR -> DetectableType.TROTTOIR;
      case LINE -> DetectableType.LINE;
      case ESPACE_VERT -> DetectableType.ESPACE_VERT;
      case VOIE_CARROSSABLE -> DetectableType.VOIE_CARROSSABLE;
      case MOISISSURE_CLAIR -> DetectableType.MOISISSURE_CLAIR;
      case MOISISSURE_COULEUR -> DetectableType.MOISISSURE_COULEUR;
      case MOISISSURE_NOIRCIE -> DetectableType.MOISISSURE_NOIRCIE;
      case MOISISSURE -> DetectableType.MOISISSURE;
      case USURE_IMPORTANTE -> DetectableType.USURE_IMPORTANTE;
      case USURE_LEGER -> DetectableType.USURE_LEGER;
      case USURE -> DetectableType.USURE;
      case FISSURE_CASSURE -> DetectableType.FISSURE_CASSURE;
      case OBSTACLE -> DetectableType.OBSTACLE;
      case CHEMINEE -> DetectableType.CHEMINEE;
      case HUMIDITE_INTENSE -> DetectableType.HUMIDITE_INTENSE;
      case HUMIDITE_CLAIR -> DetectableType.HUMIDITE_CLAIR;
      case HUMIDITE -> DetectableType.HUMIDITE;
      case RISQUE_FEU -> DetectableType.RISQUE_FEU;
      case VELUX -> DetectableType.VELUX;
      case PARKING -> DetectableType.PARKING;
      case ESPACE_VERT_PARKING -> DetectableType.ESPACE_VERT_PARKING;
      case BACKGROUND -> DetectableType.BACKGROUND;
      case ROAD -> DetectableType.ROAD;
      default -> throw new NotImplementedException("Unknown detectable object type " + rest);
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
      case TROTTOIRS -> objectTypes.addAll(detectableObjectTypeForTrottoirsModel());
      case OLD -> objectTypes.addAll(detectableObjectTypeForOldModel());
    }
    return objectTypes;
  }

  public static List<DetectableObjectType> detectableObjectTypeForToitureModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(TOITURE_REVETEMENT);
    objectTypes.add(PANNEAU_PHOTOVOLTAIQUE);
    objectTypes.add(MOISISSURE_NOIRCIE);
    objectTypes.add(MOISISSURE_CLAIR);
    objectTypes.add(MOISISSURE_COULEUR);
    objectTypes.add(MOISISSURE);
    objectTypes.add(USURE_IMPORTANTE);
    objectTypes.add(USURE_LEGER);
    objectTypes.add(USURE);
    objectTypes.add(OBSTACLE);
    objectTypes.add(CHEMINEE);
    objectTypes.add(HUMIDITE_INTENSE);
    objectTypes.add(HUMIDITE_CLAIR);
    objectTypes.add(HUMIDITE);
    objectTypes.add(VELUX);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForLomModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(PASSAGE_PIETON);
    objectTypes.add(TROTTOIR);
    objectTypes.add(VOIE_CARROSSABLE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForClimatResilienceModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(PARKING);
    objectTypes.add(PANNEAU_PHOTOVOLTAIQUE);
    objectTypes.add(ARBRE);
    objectTypes.add(ESPACE_VERT);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForZanModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(ARBRE);
    objectTypes.add(ESPACE_VERT);
    objectTypes.add(TOITURE_REVETEMENT);
    objectTypes.add(VOIE_CARROSSABLE);
    objectTypes.add(TROTTOIR);
    objectTypes.add(PARKING);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForConformitePluModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(TOITURE_REVETEMENT);
    objectTypes.add(ARBRE);
    objectTypes.add(VELUX);
    objectTypes.add(PANNEAU_PHOTOVOLTAIQUE);
    objectTypes.add(ESPACE_VERT);
    objectTypes.add(PISCINE);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForTrottoirsModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(TROTTOIR);
    objectTypes.add(VOIE_CARROSSABLE);
    objectTypes.add(ARBRE);
    objectTypes.add(ESPACE_VERT_PARKING);
    return objectTypes;
  }

  private List<DetectableObjectType> detectableObjectTypeForOldModel() {
    List<DetectableObjectType> objectTypes = new ArrayList<>();
    objectTypes.add(ARBRE);
    objectTypes.add(ESPACE_VERT);
    objectTypes.add(TOITURE_REVETEMENT);
    objectTypes.add(VOIE_CARROSSABLE);
    objectTypes.add(TROTTOIR);
    objectTypes.add(PARKING);
    objectTypes.add(RISQUE_FEU);
    return objectTypes;
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
