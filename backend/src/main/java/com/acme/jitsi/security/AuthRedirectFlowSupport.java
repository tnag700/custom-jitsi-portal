package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriComponentsBuilder;

public final class AuthRedirectFlowSupport {

  private static final int RETURN_TO_VALIDATION_PASSES = 8;
  public static final String RETURN_TO_SESSION_ATTRIBUTE =
      AuthRedirectFlowSupport.class.getName() + ".returnTo";

  private AuthRedirectFlowSupport() {}

  public static void rememberReturnTo(HttpServletRequest request, String returnTo) {
    String safeReturnTo = sanitizeReturnTo(returnTo);
    if (safeReturnTo == null) {
      clearRememberedReturnTo(request);
      return;
    }
    request.getSession(true).setAttribute(RETURN_TO_SESSION_ATTRIBUTE, safeReturnTo);
  }

  public static String consumeReturnTo(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object returnTo = session.getAttribute(RETURN_TO_SESSION_ATTRIBUTE);
    session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
    return returnTo instanceof String stringValue ? sanitizeReturnTo(stringValue) : null;
  }

  public static String buildFrontendContinueRedirect(String frontendOrigin, String returnTo) {
    String redirectBase = UriComponentsBuilder.fromUriString(frontendOrigin)
        .path("/auth/continue")
        .build()
        .toUriString();
    String safeReturnTo = sanitizeReturnTo(returnTo);
    if (safeReturnTo == null) {
      return redirectBase;
    }
    return redirectBase + "?returnTo=" + URLEncoder.encode(safeReturnTo, StandardCharsets.UTF_8);
  }

  public static String sanitizeReturnTo(String returnTo) {
    if (returnTo == null) {
      return null;
    }

    String valueToValidate = returnTo;
    // Expose nested encodings introduced across backend/query/browser boundaries, with bounded work.
    for (int pass = 0; pass < RETURN_TO_VALIDATION_PASSES; pass++) {
      if (!isSafeRelativePath(valueToValidate)) {
        return null;
      }

      String decodedValue = decodePercentEncodedAscii(valueToValidate, pass == 0);
      if (decodedValue == null) {
        return null;
      }
      if (decodedValue.equals(valueToValidate)) {
        return returnTo;
      }
      valueToValidate = decodedValue;
    }

    return null;
  }

  private static boolean isSafeRelativePath(String value) {
    if (!value.startsWith("/") || value.startsWith("//")) {
      return false;
    }

    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '\\' || character <= 0x1f || character == 0x7f) {
        return false;
      }
    }
    return true;
  }

  private static String decodePercentEncodedAscii(String value, boolean rejectMalformedEscape) {
    StringBuilder decoded = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character != '%') {
        decoded.append(character);
        continue;
      }

      int high = index + 1 < value.length() ? hexDigitValue(value.charAt(index + 1)) : -1;
      int low = index + 2 < value.length() ? hexDigitValue(value.charAt(index + 2)) : -1;
      if (high < 0 || low < 0) {
        if (rejectMalformedEscape) {
          return null;
        }
        decoded.append(character);
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

  private static int hexDigitValue(char character) {
    if (character >= '0' && character <= '9') {
      return character - '0';
    }
    if (character >= 'A' && character <= 'F') {
      return character - 'A' + 10;
    }
    if (character >= 'a' && character <= 'f') {
      return character - 'a' + 10;
    }
    return -1;
  }

  private static void clearRememberedReturnTo(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
    }
  }
}
