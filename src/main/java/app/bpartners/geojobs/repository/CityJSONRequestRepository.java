package app.bpartners.geojobs.repository;

import app.bpartners.geojobs.repository.model.cityjson.CityJSONRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CityJSONRequestRepository extends JpaRepository<CityJSONRequest, String> {}
