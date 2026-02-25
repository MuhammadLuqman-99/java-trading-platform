package com.tradingplatform.tradingapi.instruments;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JdbcInstrumentConfigService implements InstrumentConfigService {
  private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "HALTED", "DISABLED");

  private final JdbcTemplate jdbcTemplate;

  public JdbcInstrumentConfigService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<InstrumentConfigView> list(String status) {
    if (status == null || status.isBlank()) {
      String sql =
          """
          SELECT id, symbol, status, reference_price, created_at, updated_at
          FROM instruments
          ORDER BY symbol ASC
          """;
      return jdbcTemplate.query(sql, this::mapRow);
    }
    String normalizedStatus = normalizeStatus(status);
    String sql =
        """
        SELECT id, symbol, status, reference_price, created_at, updated_at
        FROM instruments
        WHERE status = ?
        ORDER BY symbol ASC
        """;
    return jdbcTemplate.query(sql, this::mapRow, normalizedStatus);
  }

  @Override
  public InstrumentConfigView findBySymbol(String symbol) {
    String normalizedSymbol = normalizeSymbol(symbol);
    return findBySymbolOptional(normalizedSymbol)
        .orElseThrow(() -> new InstrumentNotFoundException(normalizedSymbol));
  }

  @Override
  public InstrumentConfigView upsert(String symbol, String status, BigDecimal referencePrice) {
    String normalizedSymbol = normalizeSymbol(symbol);
    String normalizedStatus = normalizeStatus(status);
    requirePositive(referencePrice, "referencePrice");
    Instant now = Instant.now();

    if (findBySymbolOptional(normalizedSymbol).isPresent()) {
      String sql =
          """
          UPDATE instruments
          SET status = ?, reference_price = ?, updated_at = ?
          WHERE symbol = ?
          """;
      jdbcTemplate.update(
          sql, normalizedStatus, referencePrice, Timestamp.from(now), normalizedSymbol);
    } else {
      String sql =
          """
          INSERT INTO instruments (id, symbol, status, reference_price, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
          """;
      jdbcTemplate.update(
          sql,
          UUID.randomUUID(),
          normalizedSymbol,
          normalizedStatus,
          referencePrice,
          Timestamp.from(now),
          Timestamp.from(now));
    }

    return findBySymbol(normalizedSymbol);
  }

  @Override
  public InstrumentConfigView disable(String symbol) {
    String normalizedSymbol = normalizeSymbol(symbol);
    Instant now = Instant.now();
    String sql =
        """
        UPDATE instruments
        SET status = 'DISABLED', updated_at = ?
        WHERE symbol = ?
        """;
    int updated = jdbcTemplate.update(sql, Timestamp.from(now), normalizedSymbol);
    if (updated == 0) {
      throw new InstrumentNotFoundException(normalizedSymbol);
    }
    return findBySymbol(normalizedSymbol);
  }

  private Optional<InstrumentConfigView> findBySymbolOptional(String symbol) {
    String sql =
        """
        SELECT id, symbol, status, reference_price, created_at, updated_at
        FROM instruments
        WHERE symbol = ?
        """;
    List<InstrumentConfigView> rows = jdbcTemplate.query(sql, this::mapRow, symbol);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  private InstrumentConfigView mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new InstrumentConfigView(
        rs.getObject("id", UUID.class),
        rs.getString("symbol"),
        rs.getString("status"),
        rs.getBigDecimal("reference_price"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private static String normalizeSymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
    return symbol.trim().toUpperCase();
  }

  private static String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status must not be blank");
    }
    String normalized = status.trim().toUpperCase();
    if (!ALLOWED_STATUSES.contains(normalized)) {
      throw new IllegalArgumentException("Unsupported instrument status: " + status);
    }
    return normalized;
  }

  private static void requirePositive(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than 0");
    }
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
