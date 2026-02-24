package com.tradingplatform.tradingapi.audit;

public record AuditLogEntry(
    String actorUserId,
    String action,
    String entityType,
    String entityId,
    String beforeJson,
    String afterJson,
    AuditResult result,
    String errorCode,
    String errorMessage,
    String metadataJson) {}
