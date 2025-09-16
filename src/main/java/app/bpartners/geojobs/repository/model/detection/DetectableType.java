package app.bpartners.geojobs.repository.model.detection;

import lombok.Getter;

@Getter
public enum DetectableType {
  ROAD(0), // TODO
  TOITURE_REVETEMENT(0),
  PANNEAU_PHOTOVOLTAIQUE(0),
  PISCINE(4000),
  PASSAGE_PIETON(20_000),
  ARBRE(0),
  TROTTOIR(0),
  LINE(0),
  ESPACE_VERT(0),
  VOIE_CARROSSABLE(0),
  PARKING(0),
  MOISISSURE_COULEUR(0),
  MOISISSURE_CLAIR(0),
  MOISISSURE_NOIRCIE(0),
  MOISISSURE(0),
  USURE_LEGER(0),
  USURE_IMPORTANTE(0),
  USURE(0),
  FISSURE_CASSURE(0),
  OBSTACLE(0),
  CHEMINEE(0),
  HUMIDITE_CLAIR(0),
  HUMIDITE_INTENSE(0),
  HUMIDITE(0),
  RISQUE_FEU(0),
  VELUX(0),
  BATI_TUILES(0),
  BATI_BETON(0),
  BATI_ARDOISE(0),
  BATI_AUTRES(0),
  TOMBE(4000),
  BACKGROUND(0),
  ESPACE_VERT_PARKING(0); // TODO: to delete and separate

  private int minAreaThreshold;

  DetectableType(int minAreaThreshold) {
    this.minAreaThreshold = minAreaThreshold;
  }
}
