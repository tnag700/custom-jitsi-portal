package com.acme.jitsi.observability;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

final class TestSpanExporter implements SpanExporter {

  private final List<SpanData> spans = new CopyOnWriteArrayList<>();

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    this.spans.addAll(spans);
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    this.spans.clear();
    return CompletableResultCode.ofSuccess();
  }

  void reset() {
    spans.clear();
  }

  List<SpanData> snapshot() {
    return List.copyOf(spans);
  }

  List<SpanData> await(Predicate<List<SpanData>> predicate, Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      List<SpanData> snapshot = new ArrayList<>(spans);
      if (predicate.test(snapshot)) {
        return List.copyOf(snapshot);
      }
      Thread.sleep(25);
    }

    return List.copyOf(spans);
  }
}