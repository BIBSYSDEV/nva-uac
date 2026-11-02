package no.sikt.nva.uac.http.problem;

import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Answers a token that lacks the scope an operation requires with a 403 problem. */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

  public static final String SCOPE_MISSING =
      "The token does not grant the scope this operation requires";

  @Override
  public Response toResponse(ForbiddenException exception) {
    return Problem.forbidden(SCOPE_MISSING).toResponse();
  }
}
