package no.sikt.nva.uac.http.contract;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** The authored contract and the routes the adapter registers must name the same operations. */
@QuarkusTest
class OpenApiContractTest {

  private static final String AUTHORED_CONTRACT = "/openapi/nva-uac.yaml";
  private static final String REGISTERED_ROUTES = "/q/openapi?format=json";
  private static final String PATHS = "paths";
  private static final YAMLMapper DOCUMENTS = new YAMLMapper();

  @Test
  void shouldRegisterExactlyTheOperationsInTheContract() {
    var registered = given().when().get(REGISTERED_ROUTES).body().asString();

    assertThat(operationsIn(parse(registered))).isEqualTo(operationsIn(authoredContract()));
  }

  private static Set<String> operationsIn(JsonNode document) {
    var paths = document.get(PATHS);
    return fieldNames(paths)
        .flatMap(path -> fieldNames(paths.get(path)).map(method -> operation(method, path)))
        .collect(Collectors.toSet());
  }

  private static String operation(String method, String path) {
    return method.toUpperCase(Locale.ROOT) + " " + path;
  }

  private static Stream<String> fieldNames(JsonNode node) {
    return StreamSupport.stream(((Iterable<String>) node::fieldNames).spliterator(), false);
  }

  private static JsonNode parse(String document) {
    try {
      return DOCUMENTS.readTree(document);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static JsonNode authoredContract() {
    try (var contract = OpenApiContractTest.class.getResourceAsStream(AUTHORED_CONTRACT)) {
      return DOCUMENTS.readTree(contract);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
