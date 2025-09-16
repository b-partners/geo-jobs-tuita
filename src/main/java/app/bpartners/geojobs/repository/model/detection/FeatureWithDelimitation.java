package app.bpartners.geojobs.repository.model.detection;

import app.bpartners.geojobs.repository.model.Feature;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public final class FeatureWithDelimitation {
  @JsonProperty("feature")
  private Feature feature;

  @JsonProperty("delimitations")
  private List<Feature> delimitations;

  public FeatureWithDelimitation() {}

  public FeatureWithDelimitation(Feature feature, List<Feature> delimitations) {
    this.feature = feature;
    this.delimitations = delimitations;
  }

  public Feature feature() {
    return feature;
  }

  public List<Feature> delimitations() {
    return delimitations;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (FeatureWithDelimitation) obj;
    return Objects.equals(this.feature, that.feature)
        && Objects.equals(this.delimitations, that.delimitations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(feature, delimitations);
  }

  @Override
  public String toString() {
    return "FeatureWithDelimitation["
        + "feature="
        + feature
        + ", "
        + "delimitations="
        + delimitations
        + ']';
  }
}
