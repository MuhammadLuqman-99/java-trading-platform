package com.tradingplatform.tradingapi.risk;

import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.tradingapi.orders.CreateOrderCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JdbcRiskCheckService implements RiskCheckService {
  private static final BigDecimal ONE_BPS = new BigDecimal("10000");

  private final JdbcTemplate jdbcTemplate;

  public JdbcRiskCheckService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void validateOrder(CreateOrderCommand command) {
    InstrumentRiskView instrument =
        findInstrument(command.instrument())
            .orElseThrow(
                () ->
                    new RiskViolationException(
                        "INSTRUMENT_NOT_FOUND", "Instrument not found: " + command.instrument()));
    if (!instrument.isActive()) {
      throw new RiskViolationException(
          "INSTRUMENT_NOT_TRADABLE",
          "Instrument is not tradable: " + instrument.symbol() + " status=" + instrument.status());
    }

    AccountLimitView accountLimit =
        findAccountLimits(command.accountId())
            .orElseThrow(
                () ->
                    new RiskViolationException(
                        "ACCOUNT_LIMITS_NOT_FOUND",
                        "Account limits are not configured for account " + command.accountId()));

    BigDecimal referencePrice = instrument.referencePrice();
    BigDecimal notionalPrice = command.type() == OrderType.MARKET ? referencePrice : command.price();
    BigDecimal orderNotional = command.qty().multiply(notionalPrice);
    if (orderNotional.compareTo(accountLimit.maxOrderNotional()) > 0) {
      throw new RiskViolationException(
          "MAX_NOTIONAL_EXCEEDED",
          "Order notional "
              + orderNotional
              + " exceeds max_order_notional "
              + accountLimit.maxOrderNotional());
    }

    if (command.type() == OrderType.LIMIT) {
      BigDecimal absoluteDiff = command.price().subtract(referencePrice).abs();
      BigDecimal deviationBps =
          absoluteDiff.multiply(ONE_BPS).divide(referencePrice, 8, RoundingMode.HALF_UP);
      if (deviationBps.compareTo(BigDecimal.valueOf(accountLimit.priceBandBps())) > 0) {
        throw new RiskViolationException(
            "PRICE_BAND_EXCEEDED",
            "Price deviation "
                + deviationBps
                + " bps exceeds allowed "
                + accountLimit.priceBandBps()
                + " bps");
      }
    }
  }

  private Optional<InstrumentRiskView> findInstrument(String symbol) {
    String sql =
        """
        SELECT id, symbol, status, reference_price
        FROM instruments
        WHERE symbol = ?
        """;
    List<InstrumentRiskView> rows = jdbcTemplate.query(sql, this::mapInstrument, symbol);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  private Optional<AccountLimitView> findAccountLimits(UUID accountId) {
    String sql =
        """
        SELECT account_id, max_order_notional, price_band_bps
        FROM account_limits
        WHERE account_id = ?
        """;
    List<AccountLimitView> rows = jdbcTemplate.query(sql, this::mapAccountLimit, accountId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0));
  }

  private InstrumentRiskView mapInstrument(ResultSet rs, int rowNum) throws SQLException {
    return new InstrumentRiskView(
        rs.getObject("id", UUID.class),
        rs.getString("symbol"),
        rs.getString("status"),
        rs.getBigDecimal("reference_price"));
  }

  private AccountLimitView mapAccountLimit(ResultSet rs, int rowNum) throws SQLException {
    return new AccountLimitView(
        rs.getObject("account_id", UUID.class),
        rs.getBigDecimal("max_order_notional"),
        rs.getInt("price_band_bps"));
  }
}
