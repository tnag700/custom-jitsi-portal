package com.acme.jitsi.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;

public final class PhaseOneMonitoringMetrics {

  public static final String JOIN_ATTEMPTS_TOTAL = "jitsi.join.attempts.total";
  public static final String JOIN_SUCCESS_TOTAL = "jitsi.join.success.total";
  public static final String JOIN_FAILURE_TOTAL = "jitsi.join.failure.total";
  public static final String JOIN_LATENCY = "jitsi.join.latency";
  public static final String AUTH_REFRESH_EVENTS_TOTAL = "jitsi.auth.refresh.events.total";

  public static final String TAG_RESULT = "result";
  public static final String TAG_REASON_CATEGORY = "reason_category";
  public static final String TAG_ERROR_CODE = "error_code";
  public static final String TAG_EVENT_TYPE = "event_type";

  private static final String DEFAULT_ERROR_CODE = "none";
  private static final String DEFAULT_REASON_CATEGORY = "UNKNOWN";

  private PhaseOneMonitoringMetrics() {
  }

  public static void registerJoinMeters(MeterRegistry meterRegistry) {
    Counter.builder(JOIN_ATTEMPTS_TOTAL)
        .description("Total backend-observed join attempts")
        .register(meterRegistry);
    Counter.builder(JOIN_SUCCESS_TOTAL)
        .description("Successful backend-observed join attempts")
        .register(meterRegistry);
    Counter.builder(JOIN_FAILURE_TOTAL)
        .description("Failed backend-observed join attempts")
        .tag(TAG_REASON_CATEGORY, DEFAULT_REASON_CATEGORY)
        .tag(TAG_ERROR_CODE, DEFAULT_ERROR_CODE)
        .register(meterRegistry);
    Timer.builder(JOIN_LATENCY)
        .description("Backend-observable join latency")
        .tag(TAG_RESULT, "success")
        .publishPercentileHistogram()
        .publishPercentiles(0.5d, 0.95d, 0.99d)
        .serviceLevelObjectives(
            Duration.ofMillis(100),
            Duration.ofMillis(250),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(5))
        .register(meterRegistry);
    Timer.builder(JOIN_LATENCY)
        .description("Backend-observable join latency")
        .tag(TAG_RESULT, "fail")
        .publishPercentileHistogram()
        .publishPercentiles(0.5d, 0.95d, 0.99d)
        .serviceLevelObjectives(
            Duration.ofMillis(100),
            Duration.ofMillis(250),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(5))
        .register(meterRegistry);
  }

  public static void registerAuthRefreshMeters(MeterRegistry meterRegistry) {
    Counter.builder(AUTH_REFRESH_EVENTS_TOTAL)
        .description("Auth refresh outcomes observed by backend security listener")
        .tag(TAG_EVENT_TYPE, "unknown")
        .tag(TAG_RESULT, "success")
        .tag(TAG_ERROR_CODE, DEFAULT_ERROR_CODE)
        .register(meterRegistry);
  }

  public static String normalizeErrorCode(String errorCode) {
    if (errorCode == null || errorCode.isBlank()) {
      return DEFAULT_ERROR_CODE;
    }
    return errorCode;
  }

  public static String normalizeReasonCategory(String reasonCategory) {
    if (reasonCategory == null || reasonCategory.isBlank()) {
      return DEFAULT_REASON_CATEGORY;
    }
    return reasonCategory;
  }

  public static String normalizeResult(String result, boolean failure) {
    if (result == null || result.isBlank()) {
      return failure ? "fail" : "success";
    }

    String normalized = result.toLowerCase(Locale.ROOT);
    if ("success".equals(normalized) || "fail".equals(normalized)) {
      return normalized;
    }

    return failure ? "fail" : "success";
  }

  public static String normalizeEventType(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return "unknown";
    }
    return eventType.toLowerCase(Locale.ROOT);
  }
}