package com.tradingplatform.integration.binance;

import com.tradingplatform.domain.orders.OrderStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DatabaseBackedExchangeOrderStatusMapper implements ExchangeOrderStatusMapper {
  private static final String MISS_COUNTER = "connector.binance.status_mapping.miss";
  private static final String REFRESH_COUNTER = "connector.binance.status_mapping.refresh";

  private final ExchangeOrderStatusMappingRepository repository;
  private final MeterRegistry meterRegistry;
  private volatile Map<String, Map<String, OrderStatus>> mappingsByVenue = Map.of();

  public DatabaseBackedExchangeOrderStatusMapper(
      ExchangeOrderStatusMappingRepository repository, MeterRegistry meterRegistry) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    refresh();
  }

  @Override
  public OrderStatus toDomainStatus(String venue, String externalStatus) {
    String normalizedVenue = normalizeKey(venue, "venue");
    String normalizedExternalStatus = normalizeKey(externalStatus, "externalStatus");
    Map<String, OrderStatus> venueMappings = mappingsByVenue.get(normalizedVenue);
    if (venueMappings == null) {
      meterRegistry.counter(MISS_COUNTER, "venue", normalizedVenue).increment();
      throw new IllegalArgumentException("Unknown venue in status mapping: " + normalizedVenue);
    }
    OrderStatus mapped = venueMappings.get(normalizedExternalStatus);
    if (mapped == null) {
      meterRegistry.counter(MISS_COUNTER, "venue", normalizedVenue).increment();
      throw new IllegalArgumentException(
          "Unknown external status '" + normalizedExternalStatus + "' for venue " + normalizedVenue);
    }
    return mapped;
  }

  public synchronized void refresh() {
    Map<String, Map<String, OrderStatus>> loaded = new HashMap<>();
    for (ExchangeOrderStatusMapping row : repository.findAll()) {
      String normalizedVenue = normalizeKey(row.venue(), "venue");
      String normalizedExternalStatus = normalizeKey(row.externalStatus(), "externalStatus");
      loaded.putIfAbsent(normalizedVenue, new LinkedHashMap<>());
      OrderStatus previous =
          loaded.get(normalizedVenue).putIfAbsent(normalizedExternalStatus, row.domainStatus());
      if (previous != null && previous != row.domainStatus()) {
        throw new IllegalStateException(
            "Duplicate status mapping conflict for venue="
                + normalizedVenue
                + ", externalStatus="
                + normalizedExternalStatus);
      }
    }
    Map<String, Map<String, OrderStatus>> immutableLoaded = new HashMap<>();
    for (Map.Entry<String, Map<String, OrderStatus>> entry : loaded.entrySet()) {
      immutableLoaded.put(entry.getKey(), Map.copyOf(entry.getValue()));
    }
    mappingsByVenue = Map.copyOf(immutableLoaded);
    meterRegistry.counter(REFRESH_COUNTER).increment();
  }

  private static String normalizeKey(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
