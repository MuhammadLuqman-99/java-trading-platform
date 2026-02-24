package com.tradingplatform.tradingapi.audit;

public interface AuditLogRepository {
  void append(AuditLogEntry entry);
}
