package no.sikt.nva.uac.http.problem;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import no.sikt.nva.uac.shared.InvalidIdentifierException;

/**
 * Answers a malformed identifier with a 400 problem. The exception's message describes the required
 * form and never repeats the input, so it serves as the detail.
 */
@Provider
public class InvalidIdentifierExceptionMapper
    implements ExceptionMapper<InvalidIdentifierException> {

  @Override
  public Response toResponse(InvalidIdentifierException exception) {
    return Problem.badRequest(exception.getMessage()).toResponse();
  }
}
