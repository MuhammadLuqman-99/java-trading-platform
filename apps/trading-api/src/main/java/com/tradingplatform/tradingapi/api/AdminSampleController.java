package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.ledger.AdminFundingService;
import com.tradingplatform.tradingapi.ledger.FundingDirection;
import com.tradingplatform.tradingapi.risk.AccountLimitService;
import com.tradingplatform.tradingapi.risk.TradingControlService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSampleController {
  private final AccountLimitService accountLimitService;
  private final TradingControlService tradingControlService;
  private final AdminFundingService adminFundingService;

  public AdminSampleController(
      AccountLimitService accountLimitService,
      TradingControlService tradingControlService,
      AdminFundingService adminFundingService) {
    this.accountLimitService = accountLimitService;
    this.tradingControlService = tradingControlService;
    this.adminFundingService = adminFundingService;
  }

  @GetMapping("/ping")
  public Map<String, String> ping() {
    return Map.of("status", "ok", "scope", "admin");
  }

  @PutMapping("/limits/accounts/{accountId}")
  public AccountLimitResponse setAccountLimit(
      @PathVariable("accountId") UUID accountId,
      @Valid @RequestBody UpsertAccountLimitRequest request,
      Authentication authentication) {
    return AccountLimitResponse.from(
        accountLimitService.upsert(
            accountId, request.maxOrderNotional(), request.priceBandBps(), actor(authentication)));
  }

  @PutMapping("/trading/freeze")
  public TradingStatusResponse freezeTrading(
      @RequestBody(required = false) FreezeTradingRequest request, Authentication authentication) {
    String reason = request != null ? request.reason() : null;
    return TradingStatusResponse.from(tradingControlService.freeze(reason, actor(authentication)));
  }

  @PutMapping("/trading/unfreeze")
  public TradingStatusResponse unfreezeTrading(Authentication authentication) {
    return TradingStatusResponse.from(tradingControlService.unfreeze(actor(authentication)));
  }

  @GetMapping("/trading/status")
  public TradingStatusResponse tradingStatus() {
    return TradingStatusResponse.from(tradingControlService.get());
  }

  @PostMapping("/funding/credit")
  public FundingAdjustmentResponse credit(
      @Valid @RequestBody FundingAdjustmentRequest request, Authentication authentication) {
    UUID txId =
        adminFundingService.postAdjustment(
            request.accountId(),
            request.asset(),
            request.amount(),
            request.reason(),
            FundingDirection.CREDIT,
            actor(authentication));
    return new FundingAdjustmentResponse(
        txId,
        request.accountId(),
        request.asset(),
        FundingDirection.CREDIT,
        request.amount(),
        request.reason());
  }

  @PostMapping("/funding/debit")
  public FundingAdjustmentResponse debit(
      @Valid @RequestBody FundingAdjustmentRequest request, Authentication authentication) {
    UUID txId =
        adminFundingService.postAdjustment(
            request.accountId(),
            request.asset(),
            request.amount(),
            request.reason(),
            FundingDirection.DEBIT,
            actor(authentication));
    return new FundingAdjustmentResponse(
        txId,
        request.accountId(),
        request.asset(),
        FundingDirection.DEBIT,
        request.amount(),
        request.reason());
  }

  private static String actor(Authentication authentication) {
    if (authentication == null) {
      return "admin";
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof Jwt jwt && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
      return jwt.getSubject();
    }
    String name = authentication.getName();
    if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
      return "admin";
    }
    return name;
  }
}
