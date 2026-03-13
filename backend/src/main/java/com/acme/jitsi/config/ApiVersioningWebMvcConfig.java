package com.acme.jitsi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures API versioning strategy for all domain controllers.
 *
 * <p>Current runtime contract is {@code /api/v1} for controllers under {@code com.acme.jitsi.domains}.
 * The path matching is configured via generic prefix {@code /api/{version}} and currently allows
 * only {@code v1}.
 *
 * <p>To introduce a new API version, add it both to {@code addSupportedVersions(...)} below and
 * to {@code spring.mvc.apiversion.supported} in {@code application.yml}.
 */
@Configuration
public class ApiVersioningWebMvcConfig implements WebMvcConfigurer {

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/api/{version}", HandlerTypePredicate.forBasePackage("com.acme.jitsi.domains"));
  }

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer
        .usePathSegment(1)
        .addSupportedVersions("v1");
  }
}