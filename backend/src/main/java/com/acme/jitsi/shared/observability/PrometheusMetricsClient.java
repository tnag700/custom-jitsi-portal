package com.acme.jitsi.shared.observability;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PrometheusMetricsClient implements AutoCloseable {
  private final URI origin;
  private final JsonMapper mapper;
  private final Semaphore permits = new Semaphore(4);
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
      .followRedirects(HttpClient.Redirect.NEVER).build();

  public PrometheusMetricsClient(@Value("${app.metrics.prometheus-base-url:}") String url, JsonMapper mapper) {
    this.mapper = mapper;
    origin = url.isBlank() ? null : URI.create(url);
    if (origin != null && (!("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))
        || origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
        || origin.getFragment() != null || !(origin.getPath().isEmpty() || origin.getPath().equals("/")))) {
      throw new IllegalArgumentException("Monitoring URL must be an HTTP(S) origin");
    }
  }

  public boolean configured() { return origin != null; }

  CompletableFuture<JsonNode> queryInstant(String fixedQuery, Instant time) {
    return request("query", "query=" + encode(fixedQuery) + "&time=" + time.getEpochSecond());
  }

  CompletableFuture<JsonNode> queryRange(String fixedQuery, Instant start, Instant end, long step) {
    return request("query_range", "query=" + encode(fixedQuery) + "&start=" + start.getEpochSecond()
        + "&end=" + end.getEpochSecond() + "&step=" + step);
  }

  private CompletableFuture<JsonNode> request(String endpoint, String params) {
    if (!configured() || !permits.tryAcquire()) {
      return CompletableFuture.failedFuture(new IllegalStateException("Monitoring unavailable"));
    }
    try {
      var request = HttpRequest.newBuilder(origin.resolve("/api/v1/" + endpoint + "?" + params))
          .timeout(Duration.ofSeconds(2)).GET().build();
      var network = http.sendAsync(request,
          HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), 1_048_576));
      network.whenComplete((response, error) -> permits.release());
      var result = network.copy().orTimeout(2, TimeUnit.SECONDS).whenComplete((response, error) -> {
        if (error != null) network.cancel(true);
      }).thenApply(response -> {
        if (response.statusCode() != 200) throw new IllegalStateException("Monitoring unavailable");
        return mapper.readTree(response.body());
      });
      result.whenComplete((response, error) -> {
        if (error != null) network.cancel(true);
      });
      return result;
    } catch (RuntimeException error) {
      permits.release();
      return CompletableFuture.failedFuture(error);
    }
  }

  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

  @PreDestroy
  @Override
  public void close() { http.shutdownNow(); }
}
