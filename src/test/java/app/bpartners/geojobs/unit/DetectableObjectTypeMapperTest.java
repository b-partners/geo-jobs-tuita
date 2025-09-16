package app.bpartners.geojobs.unit;

import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.*;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.ARBRE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.ESPACE_VERT;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.PANNEAU_PHOTOVOLTAIQUE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.PARKING;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.PASSAGE_PIETON;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.PISCINE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.RISQUE_FEU;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.TOITURE_REVETEMENT;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.TROTTOIR;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.VELUX;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.VOIE_CARROSSABLE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.*;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.LINE;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectableObjectTypeMapperTest {
  DetectableObjectTypeMapper subject = new DetectableObjectTypeMapper();

  @Test
  void to_domain_ok() {
    assertEquals(DetectableType.ARBRE, subject.toDomain(ARBRE));
    assertEquals(DetectableType.TOITURE_REVETEMENT, subject.toDomain(TOITURE_REVETEMENT));
    assertEquals(DetectableType.PISCINE, subject.toDomain(PISCINE));
    assertEquals(DetectableType.PASSAGE_PIETON, subject.toDomain(PASSAGE_PIETON));
    assertEquals(DetectableType.PANNEAU_PHOTOVOLTAIQUE, subject.toDomain(PANNEAU_PHOTOVOLTAIQUE));
  }

  @Test
  void to_rest_ok() {
    assertEquals(ARBRE, subject.toRest(DetectableType.ARBRE));
    assertEquals(TOITURE_REVETEMENT, subject.toRest(DetectableType.TOITURE_REVETEMENT));
    assertEquals(PISCINE, subject.toRest(DetectableType.PISCINE));
    assertEquals(PASSAGE_PIETON, subject.toRest(DetectableType.PASSAGE_PIETON));
    assertEquals(PANNEAU_PHOTOVOLTAIQUE, subject.toRest(DetectableType.PANNEAU_PHOTOVOLTAIQUE));
    assertEquals(DetectableObjectType.LINE, subject.toRest(LINE));
    assertEquals(TROTTOIR, subject.toRest(DetectableType.TROTTOIR));
    assertEquals(ESPACE_VERT, subject.toRest(DetectableType.ESPACE_VERT));
  }

  @Test
  void map_from_model_BP_Toiture_Model() {
    var object = new DetectableObjectModel().modelName(TOITURE);

    var actual = subject.mapFromModel(object);

    var expected =
        List.of(
            TOITURE_REVETEMENT,
            PANNEAU_PHOTOVOLTAIQUE,
            MOISISSURE_NOIRCIE,
            MOISISSURE_CLAIR,
            MOISISSURE_COULEUR,
            MOISISSURE,
            USURE_IMPORTANTE,
            USURE_LEGER,
            USURE,
            OBSTACLE,
            CHEMINEE,
            HUMIDITE_INTENSE,
            HUMIDITE_CLAIR,
            HUMIDITE,
            VELUX);
    assertEquals(expected, actual);
  }

  @Test
  void map_from_model_BP_Lom_Model() {
    var object = new DetectableObjectModel().modelName(LOM);

    var actual = subject.mapFromModel(object);

    var expected = List.of(PASSAGE_PIETON, TROTTOIR, VOIE_CARROSSABLE);
    assertEquals(expected, actual);
  }

  @Test
  void map_from_model_BP_Zan_Model() {
    var object = new DetectableObjectModel().modelName(ZAN);

    var actual = subject.mapFromModel(object);

    var expected =
        List.of(ARBRE, ESPACE_VERT, TOITURE_REVETEMENT, VOIE_CARROSSABLE, TROTTOIR, PARKING);
    assertEquals(expected, actual);
  }

  @Test
  void map_from_model_BP_Conformite_Plu_Model() {
    var object = new DetectableObjectModel().modelName(CONFIRMITE_PLU);

    var actual = subject.mapFromModel(object);

    var expected =
        List.of(TOITURE_REVETEMENT, ARBRE, VELUX, PANNEAU_PHOTOVOLTAIQUE, ESPACE_VERT, PISCINE);
    assertEquals(expected, actual);
  }

  @Test
  void map_from_model_BP_Climat_Resilience_Model() {
    var object = new DetectableObjectModel().modelName(CLIMAT_RESILIENCE);

    var actual = subject.mapFromModel(object);

    var expected = List.of(PARKING, PANNEAU_PHOTOVOLTAIQUE, ARBRE, ESPACE_VERT);
    assertEquals(expected, actual);
  }

  @Test
  void map_from_modl_BP_Trottoirs_Model() {
    var object = new DetectableObjectModel().modelName(TROTTOIRS);

    var actual = subject.mapFromModel(object);

    var expected = List.of(TROTTOIR, VOIE_CARROSSABLE, ARBRE, ESPACE_VERT_PARKING);
    assertEquals(expected, actual);
  }

  @Test
  void map_from_modl_BP_Old_Model() {
    var object = new DetectableObjectModel().modelName(OLD);

    var actual = subject.mapFromModel(object);

    var expected =
        List.of(
            ARBRE,
            ESPACE_VERT,
            TOITURE_REVETEMENT,
            VOIE_CARROSSABLE,
            TROTTOIR,
            PARKING,
            RISQUE_FEU);
    assertEquals(expected, actual);
  }

  @Test
  void map_bp_zan_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, ZAN);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();
    // assertEquals(6, detectableObjectWithReferenceConfidences.size());
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ARBRE, 0.2504)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ESPACE_VERT, 0.251)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TOITURE_REVETEMENT, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.VOIE_CARROSSABLE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TROTTOIR, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.PARKING, 0.252)));
  }

  @Test
  void map_bp_confirmite_plu_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, CONFIRMITE_PLU);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();
    assertEquals(6, detectableObjectWithReferenceConfidences.size());
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TOITURE_REVETEMENT, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ARBRE, 0.2504)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.VELUX, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(
                DetectableType.PANNEAU_PHOTOVOLTAIQUE, 0.27)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ESPACE_VERT, 0.251)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.PISCINE, 0.27)));
  }

  @Test
  void map_bp_trottoirs_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, TROTTOIRS);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();
    assertEquals(4, detectableObjectWithReferenceConfidences.size());
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TROTTOIR, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ARBRE, 0.2504)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.VOIE_CARROSSABLE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ESPACE_VERT_PARKING, 0.0)));
  }

  @Test
  void map_bp_climat_resilience_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, CLIMAT_RESILIENCE);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();
    assertEquals(4, detectableObjectWithReferenceConfidences.size());
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ARBRE, 0.2504)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ESPACE_VERT, 0.251)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(
                DetectableType.PANNEAU_PHOTOVOLTAIQUE, 0.27)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.PARKING, 0.252)));
  }

  @Test
  void map_bp_toiture_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, TOITURE);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();
    assertEquals(15, detectableObjectWithReferenceConfidences.size());
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TOITURE_REVETEMENT, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(
                DetectableType.PANNEAU_PHOTOVOLTAIQUE, 0.27)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.MOISISSURE_NOIRCIE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.USURE_IMPORTANTE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.OBSTACLE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.CHEMINEE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.HUMIDITE_INTENSE, 0.0)));
  }

  @Test
  void map_bp_lom_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, LOM);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();

    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.PASSAGE_PIETON, 0.29)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TROTTOIR, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.VOIE_CARROSSABLE, 0.0)));
  }

  @Test
  void map_bp_old_model_object_type_with_its_variable_reference_confidence() {
    var detectionId = randomUUID().toString();

    var actual = subject.mapDefaultConfigurationsFromModel(detectionId, OLD);

    var detectableObjectWithReferenceConfidences =
        actual.stream()
            .map(
                objectConfiguration ->
                    new DetectableObjectWithReferenceConfidence(
                        objectConfiguration.getObjectType(),
                        objectConfiguration.getMinConfidenceForDetection()))
            .toList();

    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ARBRE, 0.2504)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.ESPACE_VERT, 0.251)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TOITURE_REVETEMENT, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.VOIE_CARROSSABLE, 0.0)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.TROTTOIR, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.PARKING, 0.252)));
    assertTrue(
        detectableObjectWithReferenceConfidences.contains(
            new DetectableObjectWithReferenceConfidence(DetectableType.RISQUE_FEU, 0.0)));
  }

  private record DetectableObjectWithReferenceConfidence(
      DetectableType detectableType, Double referenceConfidence) {}
}
