package com.acme.jitsi.domains.meetings.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MeetingJoinConfigurationReadinessService {

  private final MeetingTokenProperties properties;

  public MeetingJoinConfigurationReadinessService(MeetingTokenProperties properties) {
    this.properties = properties;
  }

  public MeetingJoinConfigurationReadiness inspect() {
    List<MeetingJoinConfigurationReadiness.ConfigurationCheck> checks = new ArrayList<>();
    checks.add(validateTokenConfig());

    JoinUrlInspection joinUrlInspection = inspectJoinUrlTemplate();
    checks.add(joinUrlInspection.check());

    return new MeetingJoinConfigurationReadiness(joinUrlInspection.publicJoinUrl(), List.copyOf(checks));
  }

  private MeetingJoinConfigurationReadiness.ConfigurationCheck validateTokenConfig() {
    List<String> missingFields = new ArrayList<>();
    if (isBlank(properties.issuer())) {
      missingFields.add("issuer");
    }
    if (isBlank(properties.audience())) {
      missingFields.add("audience");
    }
    if (isBlank(properties.algorithm())) {
      missingFields.add("algorithm");
    }
    if (isBlank(properties.signingSecret())) {
      missingFields.add("signingSecret");
    }

    if (!missingFields.isEmpty()) {
      return new MeetingJoinConfigurationReadiness.ConfigurationCheck(
          "token-config",
          "error",
          "JWT-конфиг входа не готов",
          "Не заполнены обязательные поля: " + String.join(", ", missingFields),
          List.of("Проверить app.meetings.token.*", "Перезапустить backend после исправления конфига"),
          "TOKEN_CONFIG_INVALID",
          true);
    }

    return new MeetingJoinConfigurationReadiness.ConfigurationCheck(
        "token-config",
        "ok",
        "JWT-конфиг входа готов",
        "Подпись токена и базовые параметры выпуска заданы.",
        List.of("Можно повторять вход без повторного SSO, пока сессия валидна"),
        null,
        false);
  }

  private JoinUrlInspection inspectJoinUrlTemplate() {
    String joinUrlTemplate = properties.joinUrlTemplate();
    if (isBlank(joinUrlTemplate)) {
      return new JoinUrlInspection(
          null,
          new MeetingJoinConfigurationReadiness.ConfigurationCheck(
              "join-url",
              "error",
              "URL входа в Jitsi не настроен",
              "app.meetings.token.join-url-template пустой.",
              List.of("Задать корректный HTTPS URL с #jwt=%s", "Проверить публичный адрес Jitsi"),
              "JOIN_URL_TEMPLATE_INVALID",
              true));
    }

    try {
      String resolved = joinUrlTemplate.formatted("preflight-room", "preflight-token");
      URI joinUri = URI.create(resolved);
      String publicJoinUrl = joinUri.getScheme() + "://" + joinUri.getAuthority() + "/";

      return new JoinUrlInspection(
          publicJoinUrl,
          new MeetingJoinConfigurationReadiness.ConfigurationCheck(
              "join-url",
              "ok",
              "Публичный адрес встречи готов",
              "Повторный вход будет направлен на " + publicJoinUrl,
              List.of("Если браузер ругается на сертификат, открыть этот адрес вручную один раз"),
              null,
              false));
    } catch (RuntimeException exception) {
      return new JoinUrlInspection(
          null,
          new MeetingJoinConfigurationReadiness.ConfigurationCheck(
              "join-url",
              "error",
              "URL входа в Jitsi некорректен",
              "Не удалось разобрать app.meetings.token.join-url-template: " + exception.getMessage(),
              List.of("Проверить формат URL", "Оставить jwt только во fragment (#jwt=%s)"),
              "JOIN_URL_TEMPLATE_INVALID",
              true));
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record JoinUrlInspection(
      String publicJoinUrl,
      MeetingJoinConfigurationReadiness.ConfigurationCheck check) {
  }
}