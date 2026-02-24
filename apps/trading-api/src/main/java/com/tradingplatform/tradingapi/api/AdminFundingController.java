package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.admin.funding.FundingAdjustmentResult;
import com.tradingplatform.tradingapi.admin.funding.FundingAdjustmentService;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/funding")
public class AdminFundingController {
  private final FundingAdjustmentService fundingAdjustmentService;

  public AdminFundingController(FundingAdjustmentService fundingAdjustmentService) {
    this.fundingAdjustmentService = fundingAdjustmentService;
  }

  @PostMapping("/adjustments")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AdminFundingAdjustmentResponse> adjust(
      @Valid @RequestBody AdminFundingAdjustmentRequest request) {
    FundingAdjustmentResult result =
        fundingAdjustmentService.adjust(
            request.accountId(),
            request.asset(),
            request.amount(),
            request.direction(),
            request.reason(),
            Instant.now());
    return ResponseEntity.ok(AdminFundingAdjustmentResponse.from(result));
  }
}
