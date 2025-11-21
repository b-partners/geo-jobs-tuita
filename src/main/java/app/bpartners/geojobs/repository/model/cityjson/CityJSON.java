package app.bpartners.geojobs.repository.model.cityjson;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "city_json")
@Entity
public class CityJSON implements Serializable {
  @Id private String id;

  @Column(name = "s3_file_key", nullable = false)
  private String s3FileKey;

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "city_json_request_id", nullable = false)
  private CityJSONRequest request;
}
