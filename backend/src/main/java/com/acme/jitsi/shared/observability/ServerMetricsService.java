package com.acme.jitsi.shared.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class ServerMetricsService {
  public enum MetricPeriod {
    QUARTER("15m", 900), HOUR("1h", 3_600), SIX_HOURS("6h", 21_600), DAY("24h", 86_400), WEEK("7d", 604_800);
    private final String value;
    private final long seconds;
    MetricPeriod(String value, long seconds) { this.value = value; this.seconds = seconds; }
    public String value() { return value; }
    public static MetricPeriod parse(String value) {
      for (var period : values()) if (period.value.equals(value)) return period;
      throw new IllegalArgumentException("Unknown period");
    }
  }
  public record MetricDescriptor(String id, String title, String description, String unit, String scope, List<String> views) {}
  public record MetricPoint(Instant time, Double value) {}
  public record MetricSeries(String name, List<MetricPoint> points) {}
  public record MetricReading(String id, Double value, String state, Instant measuredAt, List<MetricSeries> series) {}
  public record MetricsSnapshot(Instant generatedAt, List<MetricReading> metrics) {}
  public record Summary(String backendState, Integer cpuPercent, Integer memoryPercent, String diskState,
                        Instant measuredAt, boolean stale, boolean monitoringConfigured) {}
  private record History(Instant cachedAt, List<MetricSeries> series) {}
  private final PrometheusMetricsClient client;
  private final MetricCatalog catalog;
  private final Clock clock;
  private final ReentrantLock refreshLock = new ReentrantLock();
  // ponytail: caches belong to one backend; use a shared cache only when multiple replicas need it.
  private volatile MetricsSnapshot current = new MetricsSnapshot(Instant.EPOCH, List.of());
  private final Map<String, History> history = new LinkedHashMap<>();

  @Autowired
  public ServerMetricsService(PrometheusMetricsClient client, MetricCatalog catalog) {
    this(client, catalog, Clock.systemUTC());
  }

  ServerMetricsService(PrometheusMetricsClient client, MetricCatalog catalog, Clock clock) {
    this.client = client;
    this.catalog = catalog;
    this.clock = clock;
  }

  public List<MetricDescriptor> catalog() { return catalog.descriptors(); }

  public Summary summary() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    var snapshot = current(deadline);
    var backend = reading(snapshot, "backend.available");
    var cpu = reading(snapshot, "host.cpu");
    var memory = reading(snapshot, "host.memory");
    var disk = reading(snapshot, "host.disk");
    boolean stale = List.of(backend, cpu, memory, disk).stream().anyMatch(r -> "stale".equals(r.state()));
    Instant measured = List.of(backend, cpu, memory, disk).stream().map(MetricReading::measuredAt)
        .filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null);
    return new Summary(usable(backend) ? backend.value() == 1 ? "working" : "problem" : "unknown",
        rounded(cpu), rounded(memory), usable(disk) ? disk.value() >= 90 ? "low" : "sufficient" : "unknown",
        measured, stale, client.configured());
  }

  public MetricsSnapshot query(List<String> ids, MetricPeriod period) {
    if (ids == null || period == null || ids.size() > 12 || new HashSet<>(ids).size() != ids.size()) {
      throw new IllegalArgumentException("Invalid metric selection");
    }
    ids.forEach(catalog::require);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    var snapshot = current(deadline);
    var result = new ArrayList<MetricReading>();
    for (int offset = 0; offset < ids.size(); offset += 4) {
      var batch = ids.subList(offset, Math.min(offset + 4, ids.size()));
      var pending = new LinkedHashMap<String, CompletableFuture<JsonNode>>();
      Instant end = clock.instant();
      long step = Math.max(15, (period.seconds + 298) / 299);
      Instant start = end.minusSeconds(period.seconds);
      for (String id : batch) {
        if (cachedHistory(id, period) == null && client.configured() && System.nanoTime() < deadline) {
          pending.put(id, client.queryRange(catalog.require(id).rangeQuery(), start, end, step));
        }
      }
      for (String id : batch) {
        var cached = cachedHistory(id, period);
        if (cached == null && pending.containsKey(id)) {
          JsonNode response = await(pending.get(id), deadline);
          if (valid(response, "matrix") && response.path("data").path("result").size() == 1) {
            var samples = response.path("data").path("result").get(0).path("values");
            if (samples.isArray() && samples.size() <= 300) {
              var values = new LinkedHashMap<Long, Double>();
              for (var sample : samples) {
                if (sample.isArray() && sample.size() == 2) values.put(sample.get(0).asLong(), finite(sample.get(1)));
              }
              var points = new ArrayList<MetricPoint>();
              for (long time = start.getEpochSecond(); time <= end.getEpochSecond(); time += step) {
                points.add(new MetricPoint(Instant.ofEpochSecond(time), bounded(catalog.require(id), values.get(time))));
              }
              cached = new History(end, List.of(new MetricSeries("Значение", List.copyOf(points))));
              synchronized (history) {
                if (history.size() >= 60) history.remove(history.keySet().iterator().next());
                history.put(id + "/" + period.value, cached);
              }
            }
          }
        }
        var reading = reading(snapshot, id);
        result.add(new MetricReading(id, reading.value(), reading.state(), reading.measuredAt(),
            cached == null ? List.of() : cached.series()));
      }
    }
    return new MetricsSnapshot(clock.instant(), List.copyOf(result));
  }

  private History cachedHistory(String id, MetricPeriod period) {
    synchronized (history) {
      var entry = history.get(id + "/" + period.value);
      return entry != null && Duration.between(entry.cachedAt(), clock.instant()).getSeconds() < 60 ? entry : null;
    }
  }

  private MetricsSnapshot current(long deadline) {
    if (!client.configured()) return current;
    if (Duration.between(current.generatedAt(), clock.instant()).getSeconds() < 30) return current;
    boolean locked = false;
    try {
      locked = refreshLock.tryLock(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
      if (!locked || Duration.between(current.generatedAt(), clock.instant()).getSeconds() < 30) return current;
      var readings = new ArrayList<MetricReading>();
      var descriptors = catalog.descriptors();
      Instant now = clock.instant();
      for (int offset = 0; offset < descriptors.size(); offset += 2) {
        var batch = descriptors.subList(offset, Math.min(offset + 2, descriptors.size()));
        var values = new LinkedHashMap<String, CompletableFuture<JsonNode>>();
        var fresh = new LinkedHashMap<String, CompletableFuture<JsonNode>>();
        for (var descriptor : batch) {
          var definition = catalog.require(descriptor.id());
          if (System.nanoTime() < deadline) {
            values.put(descriptor.id(), client.queryInstant(definition.query(), now));
            fresh.put(descriptor.id(), client.queryInstant(definition.freshnessQuery(), now));
          }
        }
        for (var descriptor : batch) {
          String id = descriptor.id();
          var definition = catalog.require(id);
          JsonNode raw = await(values.get(id), deadline);
          Double value = bounded(definition, scalar(raw));
          Double sampleTime = scalar(await(fresh.get(id), deadline));
          Instant measured = sampleTime == null || sampleTime < 0 || sampleTime > now.plusSeconds(5).getEpochSecond()
              ? null : Instant.ofEpochSecond(sampleTime.longValue());
          String state = measured == null ? "unavailable" : measured.isBefore(now.minusSeconds(90))
              || measured.isAfter(now.plusSeconds(5)) ? "stale" : value == null ? "no_data" : "ok";
          if (definition.traffic() != null && System.nanoTime() < deadline) {
            Double traffic = scalar(await(client.queryInstant(definition.traffic(), now), deadline));
            if ("ok".equals(state) || "no_data".equals(state)) {
              if (traffic != null && traffic == 0) { state = "no_traffic"; value = null; }
            }
          }
          if ("ok".equals(state) && raw != null && raw.path("warnings").size() > 0) state = "partial";
          if (!"ok".equals(state) && !"partial".equals(state)) value = null;
          readings.add(new MetricReading(id, value, state, measured, List.of()));
        }
      }
      current = new MetricsSnapshot(now, List.copyOf(readings));
      return current;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return current;
    } finally {
      if (locked) refreshLock.unlock();
    }
  }

  private MetricReading reading(MetricsSnapshot snapshot, String id) {
    var reading = snapshot.metrics().stream().filter(r -> r.id().equals(id)).findFirst()
        .orElse(new MetricReading(id, null, "unavailable", null, List.of()));
    if (reading.measuredAt() != null && reading.measuredAt().isBefore(clock.instant().minusSeconds(90))) {
      return new MetricReading(id, null, "stale", reading.measuredAt(), List.of());
    }
    return reading;
  }

  private static boolean usable(MetricReading reading) { return "ok".equals(reading.state()) && reading.value() != null; }
  private static Integer rounded(MetricReading reading) { return usable(reading) ? (int) Math.round(reading.value() / 5) * 5 : null; }

  private static JsonNode await(CompletableFuture<JsonNode> pending, long deadline) {
    if (pending == null) return null;
    try { return pending.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); pending.cancel(true); return null; }
    catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) { pending.cancel(true); return null; }
  }

  private static boolean valid(JsonNode response, String type) {
    return response != null && "success".equals(response.path("status").asString())
        && type.equals(response.path("data").path("resultType").asString())
        && response.path("data").path("result").isArray();
  }

  static Double scalar(JsonNode response) {
    if (!valid(response, "vector") || response.path("data").path("result").size() != 1) return null;
    var sample = response.path("data").path("result").get(0).path("value");
    return sample.isArray() && sample.size() == 2 ? finite(sample.get(1)) : null;
  }

  private static Double finite(JsonNode value) {
    try { double number = Double.parseDouble(value.asString()); return Double.isFinite(number) ? number : null; }
    catch (RuntimeException invalid) { return null; }
  }

  private static Double bounded(MetricCatalog.Definition definition, Double value) {
    if (value == null || value < 0 || ("percent".equals(definition.descriptor().unit()) && value > 100)
        || ("boolean".equals(definition.descriptor().unit()) && value != 0 && value != 1)) return null;
    return value;
  }
}
