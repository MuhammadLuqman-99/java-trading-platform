package com.tradingplatform.tradingapi.instruments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcInstrumentConfigServiceTest {
  private JdbcTemplate jdbcTemplate;
  private JdbcInstrumentConfigService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    service = new JdbcInstrumentConfigService(jdbcTemplate);
  }

  @Test
  void shouldListByStatus() {
    InstrumentConfigView row = view("BTCUSDT", "ACTIVE", "50000");
    when(
            jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("WHERE status = ?")),
                any(RowMapper.class),
                eq("ACTIVE")))
        .thenReturn(List.of(row));

    List<InstrumentConfigView> rows = service.list("active");

    assertEquals(1, rows.size());
    assertEquals("BTCUSDT", rows.get(0).symbol());
    assertEquals("ACTIVE", rows.get(0).status());
  }

  @Test
  void shouldInsertWhenUpsertingMissingSymbol() {
    InstrumentConfigView inserted = view("ETHUSDT", "ACTIVE", "3200");
    when(
            jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("WHERE symbol = ?")),
                any(RowMapper.class),
                eq("ETHUSDT")))
        .thenReturn(List.of(), List.of(inserted));
    when(
            jdbcTemplate.update(
                argThat(sql -> sql != null && sql.contains("INSERT INTO instruments")),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
        .thenReturn(1);

    InstrumentConfigView actual = service.upsert("ethusdt", "active", new BigDecimal("3200"));

    assertEquals("ETHUSDT", actual.symbol());
    assertEquals("ACTIVE", actual.status());
    verify(jdbcTemplate)
        .update(
            argThat(sql -> sql != null && sql.contains("INSERT INTO instruments")),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void shouldDisableSymbol() {
    InstrumentConfigView disabled = view("SOLUSDT", "DISABLED", "120");
    when(jdbcTemplate.update(argThat(sql -> sql != null && sql.contains("SET status = 'DISABLED'")), any(), eq("SOLUSDT")))
        .thenReturn(1);
    when(
            jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("WHERE symbol = ?")),
                any(RowMapper.class),
                eq("SOLUSDT")))
        .thenReturn(List.of(disabled));

    InstrumentConfigView actual = service.disable("solusdt");

    assertEquals("DISABLED", actual.status());
  }

  @Test
  void shouldRejectUnsupportedStatus() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.upsert("BTCUSDT", "UNKNOWN", new BigDecimal("1")));

    assertEquals("Unsupported instrument status: UNKNOWN", ex.getMessage());
  }

  private static InstrumentConfigView view(String symbol, String status, String referencePrice) {
    Instant now = Instant.parse("2026-02-25T00:00:00Z");
    return new InstrumentConfigView(
        UUID.randomUUID(), symbol, status, new BigDecimal(referencePrice), now, now);
  }
}
