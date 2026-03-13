package com.acme.jitsi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiDocumentationConfig {

  @Bean
  public OpenAPI portalOpenApi(
      @Value("${app.openapi.server-url:http://localhost:8080}") String serverUrl) {
    return new OpenAPI()
        .info(new Info().title("Jitsi Portal API").version("0.1.0"))
        .servers(List.of(new Server().url(serverUrl)));
  }
}