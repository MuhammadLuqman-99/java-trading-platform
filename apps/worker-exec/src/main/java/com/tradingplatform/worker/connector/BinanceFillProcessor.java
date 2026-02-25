package com.tradingplatform.worker.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.infra.kafka.contract.EventTypes;
import com.tradingplatform.infra.kafka.contract.payload.BalanceUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.ExecutionRecordedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV2;
import com.tradingplatform.infra.kafka.topics.TopicNames;
import com.tradingplatform.integration.binance.BinanceTradeSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BinanceFillProcessor {
  private static final String EXCHANGE_NAME = "BINANCE";
  private static final String ORDER_AGGREGATE_TYPE = "ORDER";
  private static final String EXECUTION_AGGREGATE_TYPE = "EXECUTION";
  private static final String WALLET_BALANCE_AGGREGATE_TYPE = "WALLET_BALANCE";
  private static final String ORDER_FILL_APPLIED_EVENT_TYPE = "ORDER_FILL_APPLIED";
  private static final String BALANCE_UPDATE_REASON = "order_fill";
  private static final String LEDGER_TX_TYPE = "ORDER_FILL";
  private static final String LEDGER_REF_TYPE = "EXECUTION";
  private static final String LEDGER_REF_TYPE_OFFSET = "EXECUTION_OFFSET";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_FAILED = "failed";
  private static final String BALANCE_UPDATES_TOTAL_METRIC = "worker.balance.updates.total";
  private static final String ORDER_FILL_UPDATES_TOTAL_METRIC = "worker.order_fill.updates.total";
  private static final String OUTBOX_APPEND_TOTAL_METRIC = "worker.outbox.append.total";
  private static final List<String> KNOWN_QUOTE_ASSETS =
      List.of("USDT", "USDC", "BUSD", "FDUSD", "BTC", "ETH", "BNB", "EUR", "TRY");

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public BinanceFillProcessor(
      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public FillProcessingOutcome processTrade(BinanceTradeSnapshot trade) {
    Objects.requireNonNull(trade, "trade must not be null");
    String exchangeOrderId = requireNonBlank(trade.exchangeOrderId(), "exchangeOrderId");
    String tradeId = requireNonBlank(trade.tradeId(), "tradeId");
    BigDecimal qty = requirePositive(trade.qty(), "qty");
    BigDecimal price = requirePositive(trade.price(), "price");
    Instant executedAt = trade.tradeTime() == null ? Instant.now() : trade.tradeTime();

    OrderSnapshot order = loadOrderForUpdate(exchangeOrderId);
    if (order == null) {
      return FillProcessingOutcome.UNMAPPED;
    }

    InstrumentAssets assets = loadInstrumentAssets(order.instrument());
    String normalizedFeeAsset = normalizeAsset(trade.feeAsset());
    if (normalizedFeeAsset == null) {
      normalizedFeeAsset = assets.quoteAsset();
    }
    BigDecimal feeAmount = normalizeFeeAmount(trade.feeAmount());

    UUID executionId = UUID.randomUUID();
    if (!insertExecutionIfAbsent(
        executionId, order, tradeId, qty, price, normalizedFeeAsset, feeAmount, executedAt)) {
      return FillProcessingOutcome.DUPLICATE;
    }

    ReservationState reservation = loadActiveReservationForUpdate(order.id());
    postLedgerEntries(executionId, order, assets, qty, price, normalizedFeeAsset, feeAmount);
    applyBalanceMutations(order, assets, qty, price, normalizedFeeAsset, feeAmount, reservation, executedAt);
    OrderMutation mutation = applyOrderFill(order, qty, executedAt);
    if ("FILLED".equals(mutation.updatedStatus())) {
      releaseRemainingReservation(order.accountId(), reservation, executedAt);
    }
    persistReservationState(reservation);
    appendOrderEvent(order, mutation, tradeId, executionId, price, feeAmount, normalizedFeeAsset, executedAt);
    appendOrderUpdatedOutbox(mutation, executedAt);
    appendExecutionRecordedOutbox(
        executionId, order, tradeId, qty, price, normalizedFeeAsset, feeAmount, executedAt);

    meterRegistry
        .counter(ORDER_FILL_UPDATES_TOTAL_METRIC, "status", mutation.updatedStatus())
        .increment();
    return FillProcessingOutcome.INSERTED;
  }

  private void postLedgerEntries(
      UUID executionId,
      OrderSnapshot order,
      InstrumentAssets assets,
      BigDecimal qty,
      BigDecimal price,
      String feeAsset,
      BigDecimal feeAmount) {
    BigDecimal notional = qty.multiply(price);
    UUID transactionId = UUID.randomUUID();
    insertLedgerTransaction(transactionId, executionId.toString());

    if ("BUY".equals(order.side())) {
      appendLedgerPair(
          transactionId,
          order.accountId(),
          assets.baseAsset(),
          "CREDIT",
          qty,
          executionId.toString());
      appendLedgerPair(
          transactionId,
          order.accountId(),
          assets.quoteAsset(),
          "DEBIT",
          notional,
          executionId.toString());
    } else if ("SELL".equals(order.side())) {
      appendLedgerPair(
          transactionId,
          order.accountId(),
          assets.baseAsset(),
          "DEBIT",
          qty,
          executionId.toString());
      appendLedgerPair(
          transactionId,
          order.accountId(),
          assets.quoteAsset(),
          "CREDIT",
          notional,
          executionId.toString());
    } else {
      throw new IllegalStateException("Unsupported order side for ledger posting: " + order.side());
    }

    if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
      appendLedgerPair(
          transactionId,
          order.accountId(),
          feeAsset,
          "DEBIT",
          feeAmount,
          executionId.toString());
    }
  }

  private void insertLedgerTransaction(UUID transactionId, String correlationId) {
    String sql =
        """
        INSERT INTO ledger_transactions (id, correlation_id, type, created_at)
        VALUES (?, ?, ?, NOW())
        """;
    jdbcTemplate.update(sql, transactionId, correlationId, LEDGER_TX_TYPE);
  }

  private void appendLedgerPair(
      UUID transactionId,
      UUID accountId,
      String asset,
      String direction,
      BigDecimal amount,
      String referenceId) {
    BigDecimal normalizedAmount = requirePositive(amount, "ledgerAmount");
    appendLedgerEntry(
        transactionId, accountId, asset, direction, normalizedAmount, LEDGER_REF_TYPE, referenceId);
    appendLedgerEntry(
        transactionId,
        accountId,
        asset,
        oppositeDirection(direction),
        normalizedAmount,
        LEDGER_REF_TYPE_OFFSET,
        referenceId);
  }

  private void appendLedgerEntry(
      UUID transactionId,
      UUID accountId,
      String asset,
      String direction,
      BigDecimal amount,
      String referenceType,
      String referenceId) {
    String sql =
        """
        INSERT INTO ledger_entries (
            id,
            tx_id,
            account_id,
            asset,
            direction,
            amount,
            ref_type,
            ref_id,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """;
    jdbcTemplate.update(
        sql,
        UUID.randomUUID(),
        transactionId,
        accountId,
        asset,
        direction,
        amount,
        referenceType,
        referenceId);
  }

  private static String oppositeDirection(String direction) {
    return "DEBIT".equals(direction) ? "CREDIT" : "DEBIT";
  }

  private OrderSnapshot loadOrderForUpdate(String exchangeOrderId) {
    String sql =
        """
        SELECT id,
               account_id,
               instrument,
               side,
               qty,
               filled_qty,
               status,
               exchange_name,
               exchange_order_id,
               exchange_client_order_id
        FROM orders
        WHERE exchange_name = ?
          AND exchange_order_id = ?
        ORDER BY created_at DESC
        LIMIT 1
        FOR UPDATE
        """;
    List<OrderSnapshot> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new OrderSnapshot(
                    rs.getObject("id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getString("instrument"),
                    rs.getString("side"),
                    rs.getBigDecimal("qty"),
                    rs.getBigDecimal("filled_qty"),
                    rs.getString("status"),
                    rs.getString("exchange_name"),
                    rs.getString("exchange_order_id"),
                    rs.getString("exchange_client_order_id")),
            EXCHANGE_NAME,
            exchangeOrderId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private InstrumentAssets loadInstrumentAssets(String symbol) {
    String sql =
        """
        SELECT base_asset, quote_asset
        FROM instruments
        WHERE symbol = ?
        """;
    List<InstrumentAssets> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
              String baseAsset = normalizeAsset(rs.getString("base_asset"));
              String quoteAsset = normalizeAsset(rs.getString("quote_asset"));
              if (baseAsset == null || quoteAsset == null) {
                return deriveAssetsFromSymbol(symbol);
              }
              return new InstrumentAssets(baseAsset, quoteAsset);
            },
            symbol);
    if (rows.isEmpty()) {
      return deriveAssetsFromSymbol(symbol);
    }
    return rows.getFirst();
  }

  private static InstrumentAssets deriveAssetsFromSymbol(String symbol) {
    String normalized = requireNonBlank(symbol, "symbol").toUpperCase();
    for (String quote : KNOWN_QUOTE_ASSETS) {
      if (normalized.endsWith(quote) && normalized.length() > quote.length()) {
        String base = normalized.substring(0, normalized.length() - quote.length());
        return new InstrumentAssets(base, quote);
      }
    }
    throw new IllegalStateException("Unable to derive base/quote assets for symbol " + normalized);
  }

  private boolean insertExecutionIfAbsent(
      UUID executionId,
      OrderSnapshot order,
      String tradeId,
      BigDecimal qty,
      BigDecimal price,
      String feeAsset,
      BigDecimal feeAmount,
      Instant executedAt) {
    String sql =
        """
        INSERT INTO executions (
            id,
            order_id,
            account_id,
            instrument,
            trade_id,
            exchange_name,
            exchange_order_id,
            side,
            qty,
            price,
            fee_asset,
            fee_amount,
            executed_at,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        ON CONFLICT (exchange_name, instrument, trade_id) DO NOTHING
        """;
    int inserted =
        jdbcTemplate.update(
            sql,
            executionId,
            order.id(),
            order.accountId(),
            order.instrument(),
            tradeId,
            EXCHANGE_NAME,
            order.exchangeOrderId(),
            order.side(),
            qty,
            price,
            feeAsset,
            feeAmount,
            executedAt);
    return inserted == 1;
  }

  private void applyBalanceMutations(
      OrderSnapshot order,
      InstrumentAssets assets,
      BigDecimal qty,
      BigDecimal price,
      String feeAsset,
      BigDecimal feeAmount,
      ReservationState reservation,
      Instant executedAt) {
    Map<String, BigDecimal> deltas = new LinkedHashMap<>();
    if ("BUY".equals(order.side())) {
      addDelta(deltas, assets.baseAsset(), qty);
      addDelta(deltas, assets.quoteAsset(), qty.multiply(price).negate());
    } else if ("SELL".equals(order.side())) {
      addDelta(deltas, assets.baseAsset(), qty.negate());
      addDelta(deltas, assets.quoteAsset(), qty.multiply(price));
    } else {
      throw new IllegalStateException("Unsupported order side for fill processing: " + order.side());
    }

    if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
      addDelta(deltas, feeAsset, feeAmount.negate());
    }

    for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
      if (entry.getValue().compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }
      UpdatedBalance updatedBalance =
          applyBalanceDelta(order.accountId(), entry.getKey(), entry.getValue(), reservation);
      appendBalanceUpdatedOutbox(
          order.accountId(),
          entry.getKey(),
          updatedBalance.available(),
          updatedBalance.reserved(),
          executedAt);
    }
  }

  private UpdatedBalance applyBalanceDelta(
      UUID accountId, String asset, BigDecimal delta, ReservationState reservation) {
    WalletBalanceRow current = findBalanceForUpdate(accountId, asset);
    if (current == null) {
      if (delta.compareTo(BigDecimal.ZERO) < 0) {
        meterRegistry
            .counter(BALANCE_UPDATES_TOTAL_METRIC, "asset", asset, "outcome", OUTCOME_FAILED)
            .increment();
        throw new IllegalStateException(
            "Insufficient balance: no wallet row for account="
                + accountId
                + " asset="
                + asset
                + " delta="
                + delta);
      }
      insertBalance(accountId, asset, delta, BigDecimal.ZERO);
      meterRegistry
          .counter(BALANCE_UPDATES_TOTAL_METRIC, "asset", asset, "outcome", OUTCOME_SUCCESS)
          .increment();
      return new UpdatedBalance(delta, BigDecimal.ZERO);
    }

    BigDecimal available = current.available();
    BigDecimal reserved = current.reserved();
    BigDecimal newAvailable;
    BigDecimal newReserved;

    if (delta.compareTo(BigDecimal.ZERO) > 0) {
      newAvailable = available.add(delta);
      newReserved = reserved;
    } else {
      BigDecimal debit = delta.negate();
      BigDecimal consumeReserved = reservation.consume(asset, debit);
      BigDecimal remainingDebit = debit.subtract(consumeReserved);
      if (available.compareTo(remainingDebit) < 0) {
        meterRegistry
            .counter(BALANCE_UPDATES_TOTAL_METRIC, "asset", asset, "outcome", OUTCOME_FAILED)
            .increment();
        throw new IllegalStateException(
            "Insufficient available balance for account="
                + accountId
                + " asset="
                + asset
                + " required="
                + remainingDebit
                + " available="
                + available);
      }
      newAvailable = available.subtract(remainingDebit);
      newReserved = reserved.subtract(consumeReserved);
    }

    updateBalance(accountId, asset, newAvailable, newReserved);
    meterRegistry
        .counter(BALANCE_UPDATES_TOTAL_METRIC, "asset", asset, "outcome", OUTCOME_SUCCESS)
        .increment();
    return new UpdatedBalance(newAvailable, newReserved);
  }

  private WalletBalanceRow findBalanceForUpdate(UUID accountId, String asset) {
    String sql =
        """
        SELECT available, reserved
        FROM wallet_balances
        WHERE account_id = ?
          AND asset = ?
        FOR UPDATE
        """;
    List<WalletBalanceRow> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new WalletBalanceRow(rs.getBigDecimal("available"), rs.getBigDecimal("reserved")),
            accountId,
            asset);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private void insertBalance(UUID accountId, String asset, BigDecimal available, BigDecimal reserved) {
    String sql =
        """
        INSERT INTO wallet_balances (account_id, asset, available, reserved, updated_at)
        VALUES (?, ?, ?, ?, NOW())
        """;
    jdbcTemplate.update(sql, accountId, asset, available, reserved);
  }

  private void updateBalance(
      UUID accountId, String asset, BigDecimal available, BigDecimal reserved) {
    String sql =
        """
        UPDATE wallet_balances
        SET available = ?, reserved = ?, updated_at = NOW()
        WHERE account_id = ?
          AND asset = ?
        """;
    int updated = jdbcTemplate.update(sql, available, reserved, accountId, asset);
    if (updated != 1) {
      throw new IllegalStateException(
          "Wallet balance row missing for account=" + accountId + " asset=" + asset);
    }
  }

  private ReservationState loadActiveReservationForUpdate(UUID orderId) {
    String sql =
        """
        SELECT id, asset, amount
        FROM wallet_reservations
        WHERE order_id = ?
          AND status = 'ACTIVE'
        FOR UPDATE
        """;
    List<ReservationState> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new ReservationState(
                    rs.getObject("id", UUID.class),
                    normalizeAsset(rs.getString("asset")),
                    rs.getBigDecimal("amount")),
            orderId);
    return rows.isEmpty() ? ReservationState.none() : rows.getFirst();
  }

  private void persistReservationState(ReservationState reservation) {
    if (!reservation.touched()) {
      return;
    }
    if (reservation.released()) {
      String sql =
          """
          UPDATE wallet_reservations
          SET status = 'RELEASED', amount = 0, released_at = NOW()
          WHERE id = ?
            AND status = 'ACTIVE'
          """;
      jdbcTemplate.update(sql, reservation.id());
      return;
    }
    if (reservation.fullyConsumed()) {
      String sql =
          """
          UPDATE wallet_reservations
          SET status = 'CONSUMED', amount = 0, released_at = NOW()
          WHERE id = ?
            AND status = 'ACTIVE'
          """;
      jdbcTemplate.update(sql, reservation.id());
      return;
    }
    String sql =
        """
        UPDATE wallet_reservations
        SET amount = ?
        WHERE id = ?
          AND status = 'ACTIVE'
        """;
    jdbcTemplate.update(sql, reservation.remainingAmount(), reservation.id());
  }

  private void releaseRemainingReservation(
      UUID accountId, ReservationState reservation, Instant occurredAt) {
    if (!reservation.hasReservation()) {
      return;
    }
    BigDecimal releaseAmount = reservation.remainingAmount();
    if (releaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    WalletBalanceRow current = findBalanceForUpdate(accountId, reservation.asset());
    if (current == null) {
      throw new IllegalStateException(
          "Wallet balance row missing for reservation release account="
              + accountId
              + " asset="
              + reservation.asset());
    }
    if (current.reserved().compareTo(releaseAmount) < 0) {
      throw new IllegalStateException(
          "Insufficient reserved balance to release account="
              + accountId
              + " asset="
              + reservation.asset()
              + " required="
              + releaseAmount
              + " reserved="
              + current.reserved());
    }

    BigDecimal newAvailable = current.available().add(releaseAmount);
    BigDecimal newReserved = current.reserved().subtract(releaseAmount);
    updateBalance(accountId, reservation.asset(), newAvailable, newReserved);
    reservation.markReleased();
    meterRegistry
        .counter(BALANCE_UPDATES_TOTAL_METRIC, "asset", reservation.asset(), "outcome", OUTCOME_SUCCESS)
        .increment();
    appendBalanceUpdatedOutbox(accountId, reservation.asset(), newAvailable, newReserved, occurredAt);
  }

  private OrderMutation applyOrderFill(OrderSnapshot order, BigDecimal fillQty, Instant occurredAt) {
    BigDecimal updatedFilledQty = order.filledQty().add(fillQty);
    if (updatedFilledQty.compareTo(order.qty()) > 0) {
      updatedFilledQty = order.qty();
    }
    String updatedStatus =
        updatedFilledQty.compareTo(order.qty()) >= 0 ? "FILLED" : "PARTIALLY_FILLED";

    String sql =
        """
        UPDATE orders
        SET status = ?,
            filled_qty = ?,
            updated_at = ?
        WHERE id = ?
        """;
    int updated = jdbcTemplate.update(sql, updatedStatus, updatedFilledQty, occurredAt, order.id());
    if (updated != 1) {
      throw new IllegalStateException("Order not found for fill update: " + order.id());
    }
    return new OrderMutation(
        order.id(),
        order.accountId(),
        order.qty(),
        updatedFilledQty,
        updatedStatus,
        order.exchangeName(),
        order.exchangeOrderId(),
        order.exchangeClientOrderId(),
        order.status());
  }

  private void appendOrderEvent(
      OrderSnapshot order,
      OrderMutation mutation,
      String tradeId,
      UUID executionId,
      BigDecimal price,
      BigDecimal feeAmount,
      String feeAsset,
      Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reason", "exchange_fill");
    payload.put("tradeId", tradeId);
    payload.put("executionId", executionId.toString());
    payload.put("fillQty", mutation.filledQty().subtract(order.filledQty()));
    payload.put("fillPrice", price);
    payload.put("feeAsset", feeAsset);
    payload.put("feeAmount", feeAmount);
    payload.put("filledQty", mutation.filledQty());
    payload.put("remainingQty", mutation.qty().subtract(mutation.filledQty()));
    payload.put("occurredAt", occurredAt);

    String sql =
        """
        INSERT INTO order_events (
            id,
            order_id,
            event_type,
            from_status,
            to_status,
            payload_json,
            created_at
        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), NOW())
        """;
    jdbcTemplate.update(
        sql,
        UUID.randomUUID(),
        mutation.orderId(),
        ORDER_FILL_APPLIED_EVENT_TYPE,
        mutation.previousStatus(),
        mutation.updatedStatus(),
        toJson(payload));
  }

  private void appendOrderUpdatedOutbox(OrderMutation mutation, Instant occurredAt) {
    BigDecimal remainingQty = mutation.qty().subtract(mutation.filledQty());
    OrderUpdatedV1 payloadV1 =
        new OrderUpdatedV1(
            mutation.orderId().toString(),
            mutation.accountId().toString(),
            mutation.updatedStatus(),
            mutation.filledQty(),
            remainingQty,
            mutation.exchangeOrderId(),
            occurredAt);
    appendOutboxEvent(
        ORDER_AGGREGATE_TYPE,
        mutation.orderId().toString(),
        EventTypes.ORDER_UPDATED,
        payloadV1,
        TopicNames.ORDERS_UPDATED_V1,
        mutation.orderId().toString());

    OrderUpdatedV2 payloadV2 =
        new OrderUpdatedV2(
            mutation.orderId().toString(),
            mutation.accountId().toString(),
            mutation.updatedStatus(),
            mutation.filledQty(),
            remainingQty,
            mutation.exchangeName(),
            mutation.exchangeOrderId(),
            mutation.exchangeClientOrderId(),
            occurredAt);
    appendOutboxEvent(
        ORDER_AGGREGATE_TYPE,
        mutation.orderId().toString(),
        EventTypes.ORDER_UPDATED,
        payloadV2,
        TopicNames.ORDERS_UPDATED_V2,
        mutation.orderId().toString());
  }

  private void appendExecutionRecordedOutbox(
      UUID executionId,
      OrderSnapshot order,
      String tradeId,
      BigDecimal qty,
      BigDecimal price,
      String feeAsset,
      BigDecimal feeAmount,
      Instant executedAt) {
    ExecutionRecordedV1 payload =
        new ExecutionRecordedV1(
            executionId.toString(),
            order.id().toString(),
            order.accountId().toString(),
            tradeId,
            qty,
            price,
            feeAsset,
            feeAmount,
            executedAt);
    appendOutboxEvent(
        EXECUTION_AGGREGATE_TYPE,
        executionId.toString(),
        EventTypes.EXECUTION_RECORDED,
        payload,
        TopicNames.EXECUTIONS_RECORDED_V1,
        order.id().toString());
  }

  private void appendBalanceUpdatedOutbox(
      UUID accountId, String asset, BigDecimal available, BigDecimal reserved, Instant occurredAt) {
    BalanceUpdatedV1 payload =
        new BalanceUpdatedV1(
            accountId.toString(), asset, available, reserved, BALANCE_UPDATE_REASON, occurredAt);
    appendOutboxEvent(
        WALLET_BALANCE_AGGREGATE_TYPE,
        accountId.toString(),
        EventTypes.BALANCE_UPDATED,
        payload,
        TopicNames.BALANCES_UPDATED_V1,
        accountId.toString());
  }

  private void appendOutboxEvent(
      String aggregateType,
      String aggregateId,
      String eventType,
      Object payload,
      String topic,
      String eventKey) {
    String sql =
        """
        INSERT INTO outbox_events (
            id,
            aggregate_type,
            aggregate_id,
            event_type,
            event_payload,
            topic,
            event_key,
            status,
            attempt_count,
            created_at
        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, ?, 'NEW', 0, NOW())
        """;
    try {
      jdbcTemplate.update(
          sql, UUID.randomUUID(), aggregateType, aggregateId, eventType, toJson(payload), topic, eventKey);
      meterRegistry
          .counter(OUTBOX_APPEND_TOTAL_METRIC, "event_type", eventType, "outcome", OUTCOME_SUCCESS)
          .increment();
    } catch (RuntimeException ex) {
      meterRegistry
          .counter(OUTBOX_APPEND_TOTAL_METRIC, "event_type", eventType, "outcome", OUTCOME_FAILED)
          .increment();
      throw ex;
    }
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize outbox payload", ex);
    }
  }

  private static BigDecimal normalizeFeeAmount(BigDecimal amount) {
    if (amount == null) {
      return BigDecimal.ZERO;
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalStateException("feeAmount must not be negative");
    }
    return amount;
  }

  private static void addDelta(Map<String, BigDecimal> deltas, String asset, BigDecimal delta) {
    String normalizedAsset = normalizeAsset(asset);
    if (normalizedAsset == null) {
      throw new IllegalStateException("asset must not be blank");
    }
    deltas.merge(normalizedAsset, delta, BigDecimal::add);
  }

  private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalStateException(fieldName + " must be > 0");
    }
    return value;
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(fieldName + " must not be blank");
    }
    return value.trim();
  }

  private static String normalizeAsset(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase();
  }

  private record WalletBalanceRow(BigDecimal available, BigDecimal reserved) {}

  private record UpdatedBalance(BigDecimal available, BigDecimal reserved) {}

  private record InstrumentAssets(String baseAsset, String quoteAsset) {}

  private record OrderSnapshot(
      UUID id,
      UUID accountId,
      String instrument,
      String side,
      BigDecimal qty,
      BigDecimal filledQty,
      String status,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId) {}

  private record OrderMutation(
      UUID orderId,
      UUID accountId,
      BigDecimal qty,
      BigDecimal filledQty,
      String updatedStatus,
      String exchangeName,
      String exchangeOrderId,
      String exchangeClientOrderId,
      String previousStatus) {}

  private static final class ReservationState {
    private final UUID id;
    private final String asset;
    private BigDecimal remainingAmount;
    private boolean touched;
    private boolean fullyConsumed;
    private boolean released;

    private ReservationState(UUID id, String asset, BigDecimal remainingAmount) {
      this.id = id;
      this.asset = asset;
      this.remainingAmount = remainingAmount == null ? BigDecimal.ZERO : remainingAmount;
      this.touched = false;
      this.fullyConsumed = false;
      this.released = false;
    }

    static ReservationState none() {
      return new ReservationState(null, null, BigDecimal.ZERO);
    }

    BigDecimal consume(String targetAsset, BigDecimal requested) {
      if (id == null
          || asset == null
          || targetAsset == null
          || requested == null
          || requested.compareTo(BigDecimal.ZERO) <= 0
          || !asset.equals(targetAsset)
          || remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
        return BigDecimal.ZERO;
      }
      BigDecimal consume = requested.min(remainingAmount);
      if (consume.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.ZERO;
      }
      remainingAmount = remainingAmount.subtract(consume);
      touched = true;
      if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
        remainingAmount = BigDecimal.ZERO;
        fullyConsumed = true;
      }
      return consume;
    }

    void markReleased() {
      if (id == null) {
        return;
      }
      remainingAmount = BigDecimal.ZERO;
      touched = true;
      fullyConsumed = false;
      released = true;
    }

    boolean touched() {
      return touched && id != null;
    }

    boolean fullyConsumed() {
      return fullyConsumed;
    }

    boolean released() {
      return released;
    }

    boolean hasReservation() {
      return id != null;
    }

    UUID id() {
      return id;
    }

    String asset() {
      return asset;
    }

    BigDecimal remainingAmount() {
      return remainingAmount;
    }
  }
}
