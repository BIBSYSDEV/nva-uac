package no.sikt.nva.uac.http.problem;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Answers a missing resource, whether an unknown path or an unknown identifier, with a 404 problem.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

  public static final String NOTHING_AT_PATH = "No resource matches the request";

  @Override
  public Response toResponse(NotFoundException exception) {
    return Problem.notFound(NOTHING_AT_PATH).toResponse();
  }
}
