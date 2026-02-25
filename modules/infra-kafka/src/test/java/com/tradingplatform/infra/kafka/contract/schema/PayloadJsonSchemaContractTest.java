package com.tradingplatform.infra.kafka.contract.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingplatform.infra.kafka.contract.payload.BalanceUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.ExecutionRecordedV1;
import com.tradingplatform.infra.kafka.contract.payload.ExecutionRecordedV2;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderSubmittedV2;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV1;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV2;
import com.tradingplatform.infra.kafka.contract.payload.OrderUpdatedV3;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PayloadJsonSchemaContractTest {
  private static final String BASE_PATH = "contracts/payload-schemas/";
  private static final String ORDER_SUBMITTED_SCHEMA = BASE_PATH + "order-submitted-v1.schema.json";
  private static final String ORDER_SUBMITTED_V2_SCHEMA = BASE_PATH + "order-submitted-v2.schema.json";
  private static final String ORDER_UPDATED_SCHEMA = BASE_PATH + "order-updated-v1.schema.json";
  private static final String ORDER_UPDATED_V2_SCHEMA = BASE_PATH + "order-updated-v2.schema.json";
  private static final String EXECUTION_RECORDED_SCHEMA =
      BASE_PATH + "execution-recorded-v1.schema.json";
  private static final String EXECUTION_RECORDED_V2_SCHEMA =
      BASE_PATH + "execution-recorded-v2.schema.json";
  private static final String BALANCE_UPDATED_SCHEMA = BASE_PATH + "balance-updated-v1.schema.json";
  private static final String ORDER_UPDATED_V3_SCHEMA = BASE_PATH + "order-updated-v3.schema.json";

  private static final Map<String, String> PAYLOAD_SCHEMAS =
      Map.of(
          OrderSubmittedV1.class.getSimpleName(), ORDER_SUBMITTED_SCHEMA,
          OrderSubmittedV2.class.getSimpleName(), ORDER_SUBMITTED_V2_SCHEMA,
          OrderUpdatedV1.class.getSimpleName(), ORDER_UPDATED_SCHEMA,
          OrderUpdatedV2.class.getSimpleName(), ORDER_UPDATED_V2_SCHEMA,
          ExecutionRecordedV1.class.getSimpleName(), EXECUTION_RECORDED_SCHEMA,
          ExecutionRecordedV2.class.getSimpleName(), EXECUTION_RECORDED_V2_SCHEMA,
          OrderUpdatedV3.class.getSimpleName(), ORDER_UPDATED_V3_SCHEMA,
          BalanceUpdatedV1.class.getSimpleName(), BALANCE_UPDATED_SCHEMA);

  @Test
  void orderSubmittedV1Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "instrument": "BTCUSDT",
          "side": "BUY",
          "type": "LIMIT",
          "qty": 0.015,
          "price": 42000.00,
          "submittedAt": "2026-02-24T12:00:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(ORDER_SUBMITTED_SCHEMA, payload);
  }

  @Test
  void orderSubmittedV1Schema_shouldRejectMissingRequiredField() {
    String payload =
        """
        {
          "accountId": "acc-2001",
          "instrument": "BTCUSDT",
          "side": "BUY",
          "type": "LIMIT",
          "qty": 0.015,
          "price": 42000.00,
          "submittedAt": "2026-02-24T12:00:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertInvalid(ORDER_SUBMITTED_SCHEMA, payload, "required");
  }

  @Test
  void orderSubmittedV2Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "instrument": "BTCUSDT",
          "side": "BUY",
          "type": "MARKET",
          "qty": 0.015,
          "price": null,
          "clientOrderId": "client-1001",
          "submittedAt": "2026-02-24T12:00:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(ORDER_SUBMITTED_V2_SCHEMA, payload);
  }

  @Test
  void orderUpdatedV1Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "status": "PARTIALLY_FILLED",
          "filledQty": 0.005,
          "remainingQty": 0.010,
          "exchangeOrderId": "binance-123",
          "updatedAt": "2026-02-24T12:30:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(ORDER_UPDATED_SCHEMA, payload);
  }

  @Test
  void orderUpdatedV1Schema_shouldRejectInvalidNumericType() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "status": "PARTIALLY_FILLED",
          "filledQty": "0.005",
          "remainingQty": 0.010,
          "exchangeOrderId": "binance-123",
          "updatedAt": "2026-02-24T12:30:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertInvalid(ORDER_UPDATED_SCHEMA, payload, "number");
  }

  @Test
  void orderUpdatedV2Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "status": "ACK",
          "filledQty": 0,
          "remainingQty": 0.010,
          "exchangeName": "BINANCE",
          "exchangeOrderId": "binance-123",
          "exchangeClientOrderId": "client-1001",
          "updatedAt": "2026-02-24T12:30:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(ORDER_UPDATED_V2_SCHEMA, payload);
  }

  @Test
  void orderUpdatedV2Schema_shouldAcceptNullExchangeIdentifiers() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "status": "CANCELED",
          "filledQty": 0,
          "remainingQty": 0.010,
          "exchangeName": null,
          "exchangeOrderId": null,
          "exchangeClientOrderId": null,
          "updatedAt": "2026-02-24T12:30:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(ORDER_UPDATED_V2_SCHEMA, payload);
  }

  @Test
  void executionRecordedV1Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "executionId": "exec-5001",
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "tradeId": "trade-9001",
          "qty": 0.005,
          "price": 42100.00,
          "feeAsset": "USDT",
          "feeAmount": 0.12,
          "executedAt": "2026-02-24T12:31:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(EXECUTION_RECORDED_SCHEMA, payload);
  }

  @Test
  void executionRecordedV1Schema_shouldRejectMissingRequiredField() {
    String payload =
        """
        {
          "executionId": "exec-5001",
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "tradeId": "trade-9001",
          "qty": 0.005,
          "price": 42100.00,
          "feeAmount": 0.12,
          "executedAt": "2026-02-24T12:31:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertInvalid(EXECUTION_RECORDED_SCHEMA, payload, "required");
  }

  @Test
  void executionRecordedV2Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "executionId": "exec-5002",
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "exchangeName": "BINANCE",
          "exchangeOrderId": "100200300",
          "exchangeClientOrderId": "client-ord-1001",
          "exchangeTradeId": "501700",
          "rawExecutionType": "TRADE",
          "rawOrderStatus": "PARTIALLY_FILLED",
          "qty": 0.005,
          "price": 42100.00,
          "feeAsset": "USDT",
          "feeAmount": 0.21,
          "executedAt": "2026-02-25T12:31:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(EXECUTION_RECORDED_V2_SCHEMA, payload);
  }

  @Test
  void orderUpdatedV3Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "orderId": "ord-1001",
          "accountId": "acc-2001",
          "status": "PARTIALLY_FILLED",
          "filledQty": 0.005,
          "remainingQty": 0.005,
          "exchangeName": "BINANCE",
          "exchangeOrderId": "100200300",
          "exchangeClientOrderId": "client-ord-1001",
          "rawExecutionType": "TRADE",
          "rawOrderStatus": "PARTIALLY_FILLED",
          "updatedAt": "2026-02-25T12:31:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(ORDER_UPDATED_V3_SCHEMA, payload);
  }

  @Test
  void balanceUpdatedV1Schema_shouldAcceptValidPayload() {
    String payload =
        """
        {
          "accountId": "acc-2001",
          "asset": "USDT",
          "available": 1250.50,
          "reserved": 100.00,
          "reason": "ORDER_FILL",
          "asOf": "2026-02-24T12:32:00Z"
        }
        """;
    JsonSchemaValidatorSupport.assertValid(BALANCE_UPDATED_SCHEMA, payload);
  }

  @Test
  void balanceUpdatedV1Schema_shouldRejectInvalidDateType() {
    String payload =
        """
        {
          "accountId": "acc-2001",
          "asset": "USDT",
          "available": 1250.50,
          "reserved": 100.00,
          "reason": "ORDER_FILL",
          "asOf": 1708777920
        }
        """;
    JsonSchemaValidatorSupport.assertInvalid(BALANCE_UPDATED_SCHEMA, payload, "string");
  }

  @Test
  void payloadSchemas_shouldCoverAllCurrentPayloadContracts() {
    Set<String> expectedPayloadTypes =
        Set.of(
            OrderSubmittedV1.class.getSimpleName(),
            OrderSubmittedV2.class.getSimpleName(),
            OrderUpdatedV1.class.getSimpleName(),
            OrderUpdatedV2.class.getSimpleName(),
            OrderUpdatedV3.class.getSimpleName(),
            ExecutionRecordedV1.class.getSimpleName(),
            ExecutionRecordedV2.class.getSimpleName(),
            BalanceUpdatedV1.class.getSimpleName());

    assertEquals(expectedPayloadTypes, PAYLOAD_SCHEMAS.keySet());
  }
}
