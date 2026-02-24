package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.risk.TradingControlState;
import java.time.Instant;

public record TradingStatusResponse(
    boolean tradingFrozen, String freezeReason, String updatedBy, Instant updatedAt) {
  public static TradingStatusResponse from(TradingControlState state) {
    return new TradingStatusResponse(
        state.tradingFrozen(), state.freezeReason(), state.updatedBy(), state.updatedAt());
  }
}
