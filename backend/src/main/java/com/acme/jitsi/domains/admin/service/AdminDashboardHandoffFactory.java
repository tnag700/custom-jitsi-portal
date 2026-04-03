package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AdminDashboardHandoffFactory {

  AdminDashboardSummaryResponse.HandoffContext buildHandoffContext(
      String environment,
      String period,
      String severity,
      String errorCode,
      String category,
      String roomId,
      String meetingId,
      String incidentId) {
    return new AdminDashboardSummaryResponse.HandoffContext(
        environment,
        period,
        severity,
        normalizeOptional(errorCode),
        normalizeOptional(category),
        normalizeOptional(roomId),
        normalizeOptional(meetingId),
        normalizeOptional(incidentId));
  }

  String buildAdminHref(
      String path,
      String environment,
      String period,
      String severity,
      String errorCode,
      String category,
      String roomId,
      String meetingId) {
    List<String> params = new ArrayList<>();
    addQueryParam(params, "environment", environment);
    addQueryParam(params, "period", period);
    addQueryParam(params, "severity", severity);
    addQueryParam(params, "errorCode", errorCode);
    addQueryParam(params, "category", category);
    addQueryParam(params, "roomId", roomId);
    addQueryParam(params, "meetingId", meetingId);
    String href = path;
    if (!params.isEmpty()) {
      href = path + "?" + String.join("&", params);
    }
    return href;
  }

  String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void addQueryParam(List<String> params, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    params.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
  }
}