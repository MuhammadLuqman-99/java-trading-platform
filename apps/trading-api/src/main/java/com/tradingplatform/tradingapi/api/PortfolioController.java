package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.portfolio.PortfolioQueryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class PortfolioController {
  private final PortfolioQueryService portfolioQueryService;

  public PortfolioController(PortfolioQueryService portfolioQueryService) {
    this.portfolioQueryService = portfolioQueryService;
  }

  @GetMapping("/balances")
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<BalancesResponse> getBalances(@RequestParam("accountId") UUID accountId) {
    return ResponseEntity.ok(portfolioQueryService.getBalances(accountId));
  }

  @GetMapping("/portfolio")
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<PortfolioResponse> getPortfolio(@RequestParam("accountId") UUID accountId) {
    return ResponseEntity.ok(portfolioQueryService.getPortfolio(accountId));
  }
}
