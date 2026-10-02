package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PrometheusMetricsClientTest {
  @Test
  void releasesCapacityBeforeCompletedConsumersImmediatelySubmitTheirNextRequest() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    var arrived = new CountDownLatch(4);
    var nextRequest = new CountDownLatch(1);
    server.createContext("/api/v1/query", exchange -> {
      try (exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query.startsWith("query=up-")) {
          arrived.countDown();
          if (!arrived.await(1, TimeUnit.SECONDS)) throw new java.io.IOException("Initial requests did not arrive");
          // Hold three slots until the first consumer can submit its next request.
          if (!query.startsWith("query=up-0&") && !nextRequest.await(1, TimeUnit.SECONDS)) {
            throw new java.io.IOException("Next request did not arrive");
          }
        } else {
          nextRequest.countDown();
        }
        byte[] bytes = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
      } catch (Exception ignored) {
        // Closing/cancelling requests is expected if an assertion fails.
      }
    });
    server.start();
    try (var client = new PrometheusMetricsClient("http://127.0.0.1:" + server.getAddress().getPort(), JsonMapper.builder().build())) {
      var requests = new ArrayList<CompletableFuture<tools.jackson.databind.JsonNode>>();
      for (int i = 0; i < 4; i++) {
        int index = i;
        requests.add(client.queryInstant("up-" + index, Instant.now())
            .thenCompose(value -> client.queryInstant("next-" + index, Instant.now())));
      }
      for (var request : requests) {
        assertThat(request.get(3, TimeUnit.SECONDS).path("status").asString()).isEqualTo("success");
      }
    } finally {
      nextRequest.countDown();
      server.stop(0);
      executor.close();
    }
  }

  @Test
  void refusesRedirectOversizeAndStalledBodyAndReleasesCapacity() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    var mode = new AtomicInteger();
    var redirected = new AtomicInteger();
    server.createContext("/redirected", exchange -> {
      redirected.incrementAndGet();
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.createContext("/api/v1/query", exchange -> {
      try (exchange) {
        switch (mode.get()) {
          case 0 -> {
            exchange.getResponseHeaders().add("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
          }
          case 1 -> {
            exchange.sendResponseHeaders(200, 1_048_577);
            exchange.getResponseBody().write(new byte[1_048_577]);
          }
          case 2 -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            Thread.sleep(3_000);
          }
          default -> {
            byte[] bytes = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          }
        }
      } catch (Exception ignored) {
        // The client deliberately cancels oversized/stalled responses.
      }
    });
    server.start();
    try (var client = new PrometheusMetricsClient("http://127.0.0.1:" + server.getAddress().getPort(), JsonMapper.builder().build())) {
      for (int failureMode = 0; failureMode < 3; failureMode++) {
        mode.set(failureMode);
        long started = System.nanoTime();
        assertThatThrownBy(() -> client.queryInstant("up", Instant.now()).get(3, TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.ExecutionException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_800);
      }
      assertThat(redirected).hasValue(0);
      mode.set(3);
      for (int i = 0; i < 8; i++) {
        assertThat(client.queryInstant("up", Instant.now()).get(3, TimeUnit.SECONDS).path("status").asString()).isEqualTo("success");
      }
    } finally {
      server.stop(0);
      executor.close();
    }
  }

  @Test
  void acceptsOnlyConfiguredOriginsAndBlankDisablesMonitoring() {
    var mapper = JsonMapper.builder().build();
    for (String url : new String[] {"file:///etc/passwd", "http://user:secret@localhost", "http://localhost/path", "http://localhost?query=up", "http://localhost#secret"}) {
      assertThatThrownBy(() -> new PrometheusMetricsClient(url, mapper)).isInstanceOf(IllegalArgumentException.class);
    }
    try (var client = new PrometheusMetricsClient("", mapper)) {
      assertThat(client.configured()).isFalse();
    }
  }
}
