package com.acme.jitsi.security;

import java.util.regex.Pattern;

/** Prevents retired bearer-token request paths from reaching logs or error payloads. */
public final class SensitiveRequestPathSanitizer {

  static final String REDACTED_INVITE_VALIDATION_PATH =
      "/api/v1/invites/[redacted]/validate";
  private static final int MAX_DECODE_PASSES = 8;
  private static final Pattern LEGACY_INVITE_VALIDATION_PATH = Pattern.compile(
      "^/api/v1/invites/[^/]+/validate/?$",
      Pattern.CASE_INSENSITIVE);

  private SensitiveRequestPathSanitizer() {
  }

  public static String sanitize(String requestUri) {
    if (requestUri == null) {
      return "";
    }

    String candidate = requestUri;
    for (int pass = 0; pass < MAX_DECODE_PASSES; pass++) {
      if (LEGACY_INVITE_VALIDATION_PATH.matcher(candidate).matches()) {
        return REDACTED_INVITE_VALIDATION_PATH;
      }
      String decoded = decodePercentEncodedAscii(candidate);
      if (decoded.equals(candidate)) {
        return requestUri;
      }
      candidate = decoded;
    }

    return LEGACY_INVITE_VALIDATION_PATH.matcher(candidate).matches()
        ? REDACTED_INVITE_VALIDATION_PATH
        : requestUri;
  }

  private static String decodePercentEncodedAscii(String value) {
    StringBuilder decoded = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current != '%' || index + 2 >= value.length()) {
        decoded.append(current);
        continue;
      }

      int high = Character.digit(value.charAt(index + 1), 16);
      int low = Character.digit(value.charAt(index + 2), 16);
      if (high < 0 || low < 0) {
        decoded.append(current);
        continue;
      }

      int byteValue = high * 16 + low;
      if (byteValue <= 0x7f) {
        decoded.append((char) byteValue);
      } else {
        decoded.append(value, index, index + 3);
      }
      index += 2;
    }
    return decoded.toString();
  }
}
