package com.tradingplatform.tradingapi.admin.funding;

import com.tradingplatform.domain.wallet.InsufficientBalanceException;
import com.tradingplatform.domain.wallet.WalletBalance;
import com.tradingplatform.domain.wallet.WalletDomainException;
import com.tradingplatform.infra.kafka.contract.payload.BalanceUpdatedV1;
import com.tradingplatform.tradingapi.orders.OutboxAppendRepository;
import com.tradingplatform.tradingapi.wallet.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundingAdjustmentService {
  private final WalletRepository walletRepository;
  private final OutboxAppendRepository outboxAppendRepository;

  public FundingAdjustmentService(
      WalletRepository walletRepository, OutboxAppendRepository outboxAppendRepository) {
    this.walletRepository = walletRepository;
    this.outboxAppendRepository = outboxAppendRepository;
  }

  @Transactional
  public FundingAdjustmentResult adjust(
      UUID accountId,
      String asset,
      BigDecimal amount,
      FundingDirection direction,
      String reason,
      Instant occurredAt) {
    if (!walletRepository.accountExists(accountId)) {
      throw new WalletDomainException("Account not found: " + accountId);
    }

    String normalizedAsset = normalizeAsset(asset);
    Instant effectiveOccurredAt = occurredAt == null ? Instant.now() : occurredAt;
    WalletBalance current =
        walletRepository.findBalanceForUpdate(accountId, normalizedAsset).orElse(null);

    BigDecimal newAvailable;
    BigDecimal newReserved;

    if (direction == FundingDirection.CREDIT) {
      if (current == null) {
        newAvailable = amount;
        newReserved = BigDecimal.ZERO;
        walletRepository.insertBalance(accountId, normalizedAsset, newAvailable, newReserved);
      } else {
        newAvailable = current.available().add(amount);
        newReserved = current.reserved();
        walletRepository.updateBalance(accountId, normalizedAsset, newAvailable, newReserved);
      }
    } else {
      if (current == null) {
        throw new InsufficientBalanceException(accountId, normalizedAsset, amount, BigDecimal.ZERO);
      }
      if (current.available().compareTo(amount) < 0) {
        throw new InsufficientBalanceException(
            accountId, normalizedAsset, amount, current.available());
      }
      newAvailable = current.available().subtract(amount);
      newReserved = current.reserved();
      walletRepository.updateBalance(accountId, normalizedAsset, newAvailable, newReserved);
    }

    BalanceUpdatedV1 eventPayload =
        new BalanceUpdatedV1(
            accountId.toString(),
            normalizedAsset,
            newAvailable,
            newReserved,
            reason,
            effectiveOccurredAt);
    outboxAppendRepository.appendBalanceUpdated(accountId, eventPayload);

    return new FundingAdjustmentResult(
        accountId,
        normalizedAsset,
        direction,
        amount,
        newAvailable,
        newReserved,
        reason,
        effectiveOccurredAt);
  }

  private static String normalizeAsset(String asset) {
    String value = asset == null ? "" : asset.trim();
    if (value.isBlank()) {
      throw new WalletDomainException("Asset must not be blank");
    }
    return value.toUpperCase();
  }
}
