package com.tradingplatform.tradingapi.ledger;

import java.util.List;
import java.util.UUID;

public interface LedgerRepository {
  void createTransaction(UUID id, String correlationId, String type);

  void appendEntry(LedgerEntry entry);

  List<LedgerEntry> findEntriesByReference(String refType, String refId);
}
