package app.bpartners.geojobs.repository;

import app.bpartners.geojobs.repository.model.detection.Detection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionRepository extends JpaRepository<Detection, String> {
  Optional<Detection> findByEndToEndId(String endToEndId);

  Optional<Detection> findByZtjId(String ztjId);

  Optional<Detection> findByZdjId(String ztjId);

  List<Detection> findByCommunityOwnerIdAndCreationDatetimeBetweenOrderByCreationDatetimeDesc(
      String communityOwnerId, Instant from, Instant to, Pageable pageable);

  List<Detection> findAllByCreationDatetimeBetweenOrderByCreationDatetimeDesc(
      Instant from, Instant to, Pageable pageable);

  Optional<Detection> findByEndToEndIdAndCommunityOwnerId(
      String endToEndId, String communityOwnerId);
}
