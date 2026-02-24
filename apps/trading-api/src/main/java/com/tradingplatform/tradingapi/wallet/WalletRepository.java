package com.tradingplatform.tradingapi.wallet;

import com.tradingplatform.domain.wallet.ReservationStatus;
import com.tradingplatform.domain.wallet.WalletBalance;
import com.tradingplatform.domain.wallet.WalletReservation;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

  Optional<WalletBalance> findBalanceForUpdate(UUID accountId, String asset);

  void updateBalance(UUID accountId, String asset, BigDecimal available, BigDecimal reserved);

  void insertReservation(WalletReservation reservation);

  Optional<WalletReservation> findActiveReservationByOrderId(UUID orderId);

  void updateReservationStatus(UUID reservationId, ReservationStatus status);
}
