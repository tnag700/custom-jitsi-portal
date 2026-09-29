package com.acme.jitsi.domains.admin.service;

import java.util.List;
import java.util.Map;

public interface FrameworkReleasePort {

  Map<String, String> latestVersions(List<MonitoredFramework> frameworks);
}
