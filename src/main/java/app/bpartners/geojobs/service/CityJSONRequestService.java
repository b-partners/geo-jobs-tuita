package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.repository.model.cityjson.CityJSONRequestStatus.PROCESSING;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.CityJSONRequestCreated;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.repository.CityJSONRequestRepository;
import app.bpartners.geojobs.repository.model.cityjson.CityJSONRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CityJSONRequestService {
  private final CityJSONRequestRepository cityJSONRequestRepository;
  private final EventProducer<CityJSONRequestCreated> eventProducer;

  public CityJSONRequest process(CityJSONRequest cityJSONRequest) {
    var optionalRequest = cityJSONRequestRepository.findById(cityJSONRequest.getId());

    if (optionalRequest.isPresent()) {
      return optionalRequest.get();
    }

    var saved =
        cityJSONRequestRepository.save(cityJSONRequest.toBuilder().status(PROCESSING).build());
    eventProducer.accept(
        List.of(CityJSONRequestCreated.builder().requestId(saved.getId()).build()));

    return saved;
  }

  public CityJSONRequest getById(String requestId) {
    var optionalRequest = cityJSONRequestRepository.findById(requestId);
    if (optionalRequest.isEmpty()) {
      throw new NotFoundException(String.format("CityJSONRequest.id=%s was not found", requestId));
    }

    return optionalRequest.get();
  }
}
