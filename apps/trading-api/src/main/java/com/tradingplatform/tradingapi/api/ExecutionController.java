package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.executions.ExecutionQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ExecutionController {
  private final ExecutionQueryService executionQueryService;

  public ExecutionController(ExecutionQueryService executionQueryService) {
    this.executionQueryService = executionQueryService;
  }

  @GetMapping("/executions")
  @PreAuthorize("hasRole('TRADER')")
  public ResponseEntity<ExecutionsPageResponse> listExecutions(
      @RequestParam("accountId") UUID accountId,
      @RequestParam(name = "orderId", required = false) UUID orderId,
      @RequestParam(name = "symbol", required = false) String symbol,
      @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    ExecutionQueryService.ExecutionPage executionPage =
        executionQueryService.listExecutions(accountId, orderId, symbol, from, to, page, size);
    List<ExecutionResponse> responses =
        executionPage.executions().stream().map(ExecutionResponse::from).toList();
    return ResponseEntity.ok(
        new ExecutionsPageResponse(
            responses,
            executionPage.page(),
            executionPage.size(),
            executionPage.totalElements(),
            executionPage.totalPages()));
  }
}
