package com.tradingplatform.integration.binance;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BinanceRequestSigner {
  private final String apiSecret;
  private final long recvWindowMs;
  private final Clock clock;

  public BinanceRequestSigner(String apiSecret, long recvWindowMs, Clock clock) {
    this.apiSecret = Objects.requireNonNullElse(apiSecret, "");
    this.recvWindowMs = Math.max(1L, recvWindowMs);
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public SignedRequest sign(Map<String, String> queryParams) {
    LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
    if (queryParams != null) {
      queryParams.forEach(
          (key, value) -> {
            if (hasText(value)) {
              normalized.put(key, value);
            }
          });
    }
    normalized.put("timestamp", String.valueOf(clock.millis()));
    normalized.put("recvWindow", String.valueOf(recvWindowMs));

    String unsignedQuery = toQueryString(normalized);
    String signature = hmacSha256Hex(unsignedQuery);
    String signedQuery = unsignedQuery + "&signature=" + urlEncode(signature);
    return new SignedRequest(unsignedQuery, signature, signedQuery);
  }

  private String hmacSha256Hex(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(signatureBytes.length * 2);
      for (byte b : signatureBytes) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to sign Binance request", ex);
    }
  }

  private static String toQueryString(Map<String, String> queryParams) {
    StringBuilder query = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      if (!first) {
        query.append('&');
      }
      query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
      first = false;
    }
    return query.toString();
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record SignedRequest(String unsignedQuery, String signature, String signedQuery) {}
}
