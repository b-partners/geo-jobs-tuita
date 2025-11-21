package app.bpartners.geojobs.repository.model;

import static app.bpartners.geojobs.repository.model.GeoJobType.DETECTION_ADDRESS_CONVERSION;
import static org.hibernate.type.SqlTypes.JSON;

import app.bpartners.geojobs.job.model.JobType;
import app.bpartners.geojobs.job.model.Task;
import jakarta.persistence.Entity;
import java.io.Serializable;
import java.util.ArrayList;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class DetectionAddressConversionTask extends Task implements Serializable {
  private String layer; // TODO: unused, may be deleted ?

  private String address;

  @JdbcTypeCode(JSON)
  private Feature feature;

  @Override
  public JobType getJobType() {
    return DETECTION_ADDRESS_CONVERSION;
  }

  @Override
  public Task semanticClone() {
    return this.toBuilder().statusHistory(new ArrayList<>(getStatusHistory())).build();
  }
}
