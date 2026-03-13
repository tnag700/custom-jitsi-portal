package com.acme.jitsi.contracts;

import com.acme.jitsi.Application;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

public final class OpenApiSpecGenerator {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
  private static final String GENERATED_MARKER =
      "Generated from backend runtime contract. Do not edit by hand.";

  private OpenApiSpecGenerator() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected a single output path argument");
    }

    Path outputPath = Path.of(args[0]).toAbsolutePath().normalize();
    Files.createDirectories(outputPath.getParent());

    SpringApplication application = new SpringApplication(Application.class);
    application.setAdditionalProfiles("test");
    application.setDefaultProperties(Map.ofEntries(
        Map.entry("server.port", "0"),
        Map.entry("spring.datasource.url", "jdbc:h2:mem:openapi-spec;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"),
        Map.entry("spring.datasource.driver-class-name", "org.h2.Driver"),
        Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
        Map.entry("spring.flyway.enabled", "true"),
        Map.entry("management.health.redis.enabled", "false"),
        Map.entry("app.meetings.token.issuer", "https://portal.example.test"),
        Map.entry("app.meetings.token.audience", "jitsi-meet"),
        Map.entry("app.meetings.token.algorithm", "HS256"),
        Map.entry("app.meetings.token.ttl-minutes", "20"),
        Map.entry("app.meetings.token.signing-secret", "01234567890123456789012345678901"),
        Map.entry("app.meetings.token.role-claim-name", "role"),
        Map.entry("app.auth.refresh.idle-ttl-minutes", "60"),
        Map.entry("app.security.jwt-contour.issuer", "https://portal.example.test"),
        Map.entry("app.security.jwt-contour.audience", "jitsi-meet"),
        Map.entry("app.security.jwt-contour.role-claim", "role"),
        Map.entry("app.security.jwt-contour.algorithm", "HS256"),
        Map.entry("app.security.jwt-contour.access-ttl-minutes", "20"),
        Map.entry("app.security.jwt-contour.refresh-ttl-minutes", "60"),
        Map.entry("app.openapi.server-url", "http://localhost:8080")
    ));

    try (ConfigurableApplicationContext context = application.run()) {
      OpenApiWebMvcResource openApiResource = context.getBean(OpenApiWebMvcResource.class);
      MockHttpServletRequest request = createOpenApiRequest();
      String body = new String(
          openApiResource.openapiJson(request, "/v3/api-docs", Locale.ROOT),
          StandardCharsets.UTF_8);
      ObjectNode root = (ObjectNode) sortRecursively(JSON_MAPPER.readTree(body));
      root.put("x-generated-from", GENERATED_MARKER);
      Files.writeString(
          outputPath,
          JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + System.lineSeparator(),
          StandardCharsets.UTF_8);
    }
  }

  private static MockHttpServletRequest createOpenApiRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setRequestURI("/v3/api-docs");
    request.setServletPath("/v3/api-docs");
    request.setContextPath("");
    return request;
  }

  private static JsonNode sortRecursively(JsonNode node) {
    if (node.isObject()) {
      ObjectNode sorted = JSON_MAPPER.createObjectNode();
      Map<String, JsonNode> orderedFields = new TreeMap<>();
      for (Map.Entry<String, JsonNode> entry : ((ObjectNode) node).properties()) {
        orderedFields.put(entry.getKey(), sortRecursively(entry.getValue()));
      }
      orderedFields.forEach(sorted::set);
      return sorted;
    }

    if (node.isArray()) {
      ArrayNode array = JSON_MAPPER.createArrayNode();
      for (JsonNode item : node) {
        array.add(sortRecursively(item));
      }
      return array;
    }

    return node;
  }
}