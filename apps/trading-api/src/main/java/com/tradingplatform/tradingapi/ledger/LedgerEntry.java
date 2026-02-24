package com.tradingplatform.tradingapi.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntry(
    UUID id,
    UUID txId,
    UUID accountId,
    String asset,
    EntryDirection direction,
    BigDecimal amount,
    String refType,
    String refId) {}
