package com.tradingplatform.integration.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class JacksonBinanceExecutionReportParser implements BinanceExecutionReportParser {
  private static final Pattern POSITIVE_INTEGER_PATTERN = Pattern.compile("^[1-9][0-9]*$");
  private static final String EXECUTION_REPORT = "executionreport";
  private static final String TRADE = "trade";

  private final ObjectMapper objectMapper;

  public JacksonBinanceExecutionReportParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<BinanceExecutionReport> parse(String rawPayload) {
    if (rawPayload == null || rawPayload.isBlank()) {
      throw new IllegalArgumentException("rawPayload must not be blank");
    }
    JsonNode root = parseRoot(rawPayload);
    if (!isExecutionReport(root) || !isTradeExecution(root)) {
      return Optional.empty();
    }

    Optional<String> tradeId = optionalText(root, "t").filter(value -> !value.isBlank());
    if (tradeId.isEmpty() || !POSITIVE_INTEGER_PATTERN.matcher(tradeId.get()).matches()) {
      return Optional.empty();
    }

    BigDecimal cumulativeQty = decimal(root, "z");
    if (cumulativeQty.compareTo(BigDecimal.ZERO) <= 0) {
      return Optional.empty();
    }

    BigDecimal lastQty = decimal(root, "l");
    BigDecimal price = decimal(root, "L");
    BigDecimal feeAmount = optionalDecimal(root, "n").orElse(BigDecimal.ZERO);
    String feeAsset = optionalText(root, "N").filter(value -> !value.isBlank()).orElse("UNKNOWN");
    Instant tradeTime = Instant.ofEpochMilli(longValue(root, "T"));

    return Optional.of(
        new BinanceExecutionReport(
            text(root, "X"),
            text(root, "i"),
            text(root, "c"),
            text(root, "s"),
            text(root, "S"),
            tradeId.get(),
            lastQty,
            cumulativeQty,
            price,
            feeAsset,
            feeAmount,
            tradeTime,
            rawPayload));
  }

  private JsonNode parseRoot(String rawPayload) {
    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Binance execution report payload must be a JSON object");
      }
      return root;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Failed to parse Binance execution report JSON", ex);
    }
  }

  private static boolean isExecutionReport(JsonNode root) {
    return optionalText(root, "e")
        .map(value -> EXECUTION_REPORT.equals(value.toLowerCase(Locale.ROOT)))
        .orElse(false);
  }

  private static boolean isTradeExecution(JsonNode root) {
    return optionalText(root, "x")
        .map(value -> TRADE.equals(value.toLowerCase(Locale.ROOT)))
        .orElse(false);
  }

  private static String text(JsonNode root, String fieldName) {
    return optionalText(root, fieldName)
        .filter(value -> !value.isBlank())
        .orElseThrow(() -> new IllegalArgumentException("Missing required field '" + fieldName + "'"));
  }

  private static Optional<String> optionalText(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null || node.isNull()) {
      return Optional.empty();
    }
    return Optional.of(node.asText());
  }

  private static BigDecimal decimal(JsonNode root, String fieldName) {
    return optionalDecimal(root, fieldName)
        .orElseThrow(() -> new IllegalArgumentException("Missing required decimal field '" + fieldName + "'"));
  }

  private static Optional<BigDecimal> optionalDecimal(JsonNode root, String fieldName) {
    return optionalText(root, fieldName)
        .filter(value -> !value.isBlank())
        .map(
            value -> {
              try {
                return new BigDecimal(value);
              } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                    "Field '" + fieldName + "' is not a valid decimal: " + value, ex);
              }
            });
  }

  private static long longValue(JsonNode root, String fieldName) {
    String value = text(root, fieldName);
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' is not a valid long: " + value, ex);
    }
  }
}
