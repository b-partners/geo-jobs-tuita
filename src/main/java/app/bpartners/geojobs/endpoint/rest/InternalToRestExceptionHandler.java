package app.bpartners.geojobs.endpoint.rest;

import app.bpartners.geojobs.endpoint.rest.model.RestException;
import app.bpartners.geojobs.model.exception.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class InternalToRestExceptionHandler {

  @ExceptionHandler(value = {BadRequestException.class})
  ResponseEntity<RestException> handleBadRequest(BadRequestException e) {
    log.info("Bad request", e);
    return new ResponseEntity<>(toRest(e, HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(value = {MissingServletRequestParameterException.class})
  ResponseEntity<RestException> handleBadRequest(MissingServletRequestParameterException e) {
    log.info("Missing parameter", e);
    return handleBadRequest(new BadRequestException(e.getMessage()));
  }

  @ExceptionHandler(value = {HttpRequestMethodNotSupportedException.class})
  ResponseEntity<RestException> handleBadRequest(HttpRequestMethodNotSupportedException e) {
    log.info("Unsupported method for this endpoint", e);
    return handleBadRequest(new BadRequestException(e.getMessage()));
  }

  @ExceptionHandler(value = {HttpMessageNotReadableException.class})
  ResponseEntity<RestException> handleBadRequest(HttpMessageNotReadableException e) {
    Throwable cause = e.getCause();

    if (cause instanceof InvalidFormatException ife) {
      List<String> errors =
          ife.getPath().stream()
              .map(
                  ref ->
                      "Field '"
                          + ref.getFieldName()
                          + "' expects a value of type "
                          + ife.getTargetType().getSimpleName()
                          + " but got '"
                          + ife.getValue()
                          + "'")
              .toList();

      return handleBadRequest(new BadRequestException(errors.toString()));
    }

    if (cause instanceof JsonMappingException jme) {
      List<String> errors =
          jme.getPath().stream()
              .filter(reference -> !"null".equals(reference.getFieldName()))
              .map(ref -> "Problem with field: '" + ref.getFieldName() + "'")
              .toList();

      return handleBadRequest(new BadRequestException(errors.toString()));
    }
    log.info("Missing required body", e);
    return handleBadRequest(new BadRequestException(e.getMessage()));
  }

  @ExceptionHandler(value = {MethodArgumentTypeMismatchException.class})
  ResponseEntity<RestException> handleConversionFailed(MethodArgumentTypeMismatchException e) {
    var name = e.getName();
    var type = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
    var value = String.valueOf(e.getValue());
    var message =
        String.format("Parameter '%s' should be of type '%s' but got '%s'", name, type, value);
    log.info("Conversion failed", e);
    return handleBadRequest(new BadRequestException(message));
  }

  @ExceptionHandler(value = {TooManyRequestsException.class})
  ResponseEntity<RestException> handleTooManyRequests(TooManyRequestsException e) {
    log.info("Too many requests", e);
    return new ResponseEntity<>(
        toRest(e, HttpStatus.TOO_MANY_REQUESTS), HttpStatus.TOO_MANY_REQUESTS);
  }

  @ExceptionHandler(value = {ForbiddenException.class})
  ResponseEntity<RestException> handleDefault(ForbiddenException e) {
    log.error("Authentication error", e);
    return new ResponseEntity<>(toRest(e, HttpStatus.FORBIDDEN), HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(value = {NotFoundException.class})
  ResponseEntity<RestException> handleNotFound(NotFoundException e) {
    log.info("Not found", e);
    return new ResponseEntity<>(toRest(e, HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(value = {NotImplementedException.class})
  ResponseEntity<RestException> handleNotFound(NotImplementedException e) {
    log.info("Not implemented", e);
    return new ResponseEntity<>(toRest(e, HttpStatus.NOT_IMPLEMENTED), HttpStatus.NOT_IMPLEMENTED);
  }

  @ExceptionHandler(value = {UnsupportedDetectionAreaException.class})
  ResponseEntity<RestException> handleUnsupportedDetectionArea(
      UnsupportedDetectionAreaException e) {
    log.info("Detection area not supported", e);
    return new ResponseEntity<>(toRest(e, HttpStatus.NOT_IMPLEMENTED), HttpStatus.NOT_IMPLEMENTED);
  }

  @ExceptionHandler(value = {java.lang.Exception.class})
  ResponseEntity<RestException> handleDefault(java.lang.Exception e) {
    log.error("Internal error", e);
    return new ResponseEntity<>(
        toRest(e, HttpStatus.INTERNAL_SERVER_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private RestException toRest(java.lang.Exception e, HttpStatus status) {
    var restException = new RestException();
    restException.setType(status.toString());
    restException.setMessage(e.getMessage());
    return restException;
  }
}
