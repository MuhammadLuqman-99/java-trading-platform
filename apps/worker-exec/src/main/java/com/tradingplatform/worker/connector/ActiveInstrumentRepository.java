package com.tradingplatform.worker.connector;

import java.util.List;

public interface ActiveInstrumentRepository {
  List<String> findActiveSymbols();
}
