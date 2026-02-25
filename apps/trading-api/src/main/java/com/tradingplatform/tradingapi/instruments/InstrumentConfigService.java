package com.tradingplatform.tradingapi.instruments;

import java.math.BigDecimal;
import java.util.List;

public interface InstrumentConfigService {
  List<InstrumentConfigView> list(String status);

  InstrumentConfigView findBySymbol(String symbol);

  InstrumentConfigView upsert(String symbol, String status, BigDecimal referencePrice);

  InstrumentConfigView disable(String symbol);
}
