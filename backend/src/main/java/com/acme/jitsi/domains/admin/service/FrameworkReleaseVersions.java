package com.acme.jitsi.domains.admin.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FrameworkReleaseVersions {

  private static final Pattern VERSION = Pattern.compile(
      "^([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})(?:\\.([0-9]{1,9}))?"
          + "(?:-beta\\.([0-9]{1,9}))?$");

  private FrameworkReleaseVersions() {
  }

  public static Integer compare(String left, String right) {
    if (left == null || right == null) {
      return null;
    }
    Matcher first = VERSION.matcher(left);
    Matcher second = VERSION.matcher(right);
    if (!first.matches() || !second.matches()) {
      return null;
    }
    int numericOrder = compareNumbers(first, second);
    return numericOrder == 0 ? compareBeta(first.group(5), second.group(5)) : numericOrder;
  }

  private static int compareNumbers(Matcher first, Matcher second) {
    for (int group = 1; group <= 4; group++) {
      int difference = Integer.compare(
          number(first.group(group)), number(second.group(group)));
      if (difference != 0) {
        return difference;
      }
    }
    return 0;
  }

  private static int compareBeta(String firstBeta, String secondBeta) {
    if (firstBeta == null && secondBeta != null) {
      return 1;
    }
    if (firstBeta != null && secondBeta == null) {
      return -1;
    }
    return firstBeta == null ? 0 : Integer.compare(number(firstBeta), number(secondBeta));
  }

  private static int number(String value) {
    return value == null ? 0 : Integer.parseInt(value);
  }
}
