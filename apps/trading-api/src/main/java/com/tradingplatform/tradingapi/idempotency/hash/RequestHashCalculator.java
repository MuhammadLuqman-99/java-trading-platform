package com.tradingplatform.tradingapi.idempotency.hash;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequestHashCalculator {
  public String compute(HttpServletRequest request, byte[] requestBodyBytes) {
    MessageDigest digest = sha256Digest();
    update(digest, request.getMethod());
    update(digest, "\n");
    update(digest, request.getRequestURI());
    update(digest, "\n");
    update(digest, canonicalQuery(request.getParameterMap()));
    update(digest, "\n");
    if (requestBodyBytes != null && requestBodyBytes.length > 0) {
      digest.update(requestBodyBytes);
    }
    return toHex(digest.digest());
  }

  private String canonicalQuery(Map<String, String[]> parameterMap) {
    if (parameterMap == null || parameterMap.isEmpty()) {
      return "";
    }

    List<String> keys = new ArrayList<>(parameterMap.keySet());
    keys.sort(String::compareTo);

    StringBuilder builder = new StringBuilder();
    for (String key : keys) {
      String[] values = parameterMap.get(key);
      if (values == null || values.length == 0) {
        appendParam(builder, key, "");
        continue;
      }

      String[] sortedValues = Arrays.copyOf(values, values.length);
      Arrays.sort(sortedValues);
      for (String value : sortedValues) {
        appendParam(builder, key, value == null ? "" : value);
      }
    }

    return builder.toString();
  }

  private static void appendParam(StringBuilder builder, String key, String value) {
    if (!builder.isEmpty()) {
      builder.append('&');
    }
    builder.append(key).append('=').append(value);
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest unavailable", ex);
    }
  }

  private static void update(MessageDigest digest, String value) {
    digest.update(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder.append(Character.forDigit((b >> 4) & 0xF, 16));
      builder.append(Character.forDigit((b & 0xF), 16));
    }
    return builder.toString();
  }
}
