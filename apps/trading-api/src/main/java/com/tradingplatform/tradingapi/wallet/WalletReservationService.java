package com.tradingplatform.tradingapi.wallet;

import com.tradingplatform.domain.wallet.InsufficientBalanceException;
import com.tradingplatform.domain.wallet.ReservationStatus;
import com.tradingplatform.domain.wallet.WalletBalance;
import com.tradingplatform.domain.wallet.WalletDomainException;
import com.tradingplatform.domain.wallet.WalletReservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletReservationService {
  private final WalletRepository walletRepository;

  public WalletReservationService(WalletRepository walletRepository) {
    this.walletRepository = walletRepository;
  }

  @Transactional
  public WalletReservation reserve(UUID accountId, String asset, BigDecimal amount, UUID orderId) {
    WalletBalance balance =
        walletRepository
            .findBalanceForUpdate(accountId, asset)
            .orElseThrow(
                () ->
                    new WalletDomainException(
                        "No wallet balance found for account " + accountId + " asset " + asset));

    if (balance.available().compareTo(amount) < 0) {
      throw new InsufficientBalanceException(accountId, asset, amount, balance.available());
    }

    BigDecimal newAvailable = balance.available().subtract(amount);
    BigDecimal newReserved = balance.reserved().add(amount);
    walletRepository.updateBalance(accountId, asset, newAvailable, newReserved);

    WalletReservation reservation =
        new WalletReservation(
            UUID.randomUUID(),
            accountId,
            asset,
            amount,
            orderId,
            ReservationStatus.ACTIVE,
            Instant.now(),
            null);
    walletRepository.insertReservation(reservation);
    return reservation;
  }

  @Transactional
  public void release(UUID orderId) {
    Optional<WalletReservation> maybeReservation =
        walletRepository.findActiveReservationByOrderId(orderId);
    if (maybeReservation.isEmpty()) {
      return;
    }
    WalletReservation reservation = maybeReservation.get();

    WalletBalance balance =
        walletRepository
            .findBalanceForUpdate(reservation.accountId(), reservation.asset())
            .orElseThrow(() -> new WalletDomainException("Balance row not found"));

    BigDecimal newAvailable = balance.available().add(reservation.amount());
    BigDecimal newReserved = balance.reserved().subtract(reservation.amount());
    walletRepository.updateBalance(
        reservation.accountId(), reservation.asset(), newAvailable, newReserved);
    walletRepository.updateReservationStatus(reservation.id(), ReservationStatus.CANCELLED);
  }

  @Transactional
  public void consume(UUID orderId) {
    Optional<WalletReservation> maybeReservation =
        walletRepository.findActiveReservationByOrderId(orderId);
    if (maybeReservation.isEmpty()) {
      return;
    }
    WalletReservation reservation = maybeReservation.get();

    WalletBalance balance =
        walletRepository
            .findBalanceForUpdate(reservation.accountId(), reservation.asset())
            .orElseThrow(() -> new WalletDomainException("Balance row not found"));

    BigDecimal newReserved = balance.reserved().subtract(reservation.amount());
    walletRepository.updateBalance(
        reservation.accountId(), reservation.asset(), balance.available(), newReserved);
    walletRepository.updateReservationStatus(reservation.id(), ReservationStatus.CONSUMED);
  }
}
