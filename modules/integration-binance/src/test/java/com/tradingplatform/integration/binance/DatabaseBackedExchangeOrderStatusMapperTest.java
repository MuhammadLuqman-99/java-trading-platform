package com.tradingplatform.integration.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingplatform.domain.orders.OrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseBackedExchangeOrderStatusMapperTest {
  @Test
  void shouldMapKnownStatus() {
    ExchangeOrderStatusMappingRepository repository =
        () ->
            List.of(
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT, "NEW", OrderStatus.NEW, false, true));
    DatabaseBackedExchangeOrderStatusMapper mapper =
        new DatabaseBackedExchangeOrderStatusMapper(repository, new SimpleMeterRegistry());

    OrderStatus actual = mapper.toDomainStatus(BinanceVenue.BINANCE_SPOT, "new");

    assertEquals(OrderStatus.NEW, actual);
  }

  @Test
  void shouldIncrementMissCounterForUnknownStatus() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ExchangeOrderStatusMappingRepository repository =
        () ->
            List.of(
                new ExchangeOrderStatusMapping(
                    BinanceVenue.BINANCE_SPOT, "NEW", OrderStatus.NEW, false, true));
    DatabaseBackedExchangeOrderStatusMapper mapper =
        new DatabaseBackedExchangeOrderStatusMapper(repository, registry);

    assertThrows(
        IllegalArgumentException.class,
        () -> mapper.toDomainStatus(BinanceVenue.BINANCE_SPOT, "UNKNOWN_STATUS"));

    double count =
        registry
            .get("connector.binance.status_mapping.miss")
            .tag("venue", BinanceVenue.BINANCE_SPOT)
            .counter()
            .count();
    assertEquals(1.0d, count);
  }
}
