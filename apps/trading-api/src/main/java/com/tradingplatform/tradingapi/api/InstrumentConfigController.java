package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.instruments.InstrumentConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class InstrumentConfigController {
  private final InstrumentConfigService instrumentConfigService;

  public InstrumentConfigController(InstrumentConfigService instrumentConfigService) {
    this.instrumentConfigService = instrumentConfigService;
  }

  @GetMapping("/instruments")
  public ResponseEntity<List<InstrumentResponse>> list(
      @RequestParam(name = "status", required = false) String status) {
    List<InstrumentResponse> response =
        instrumentConfigService.list(status).stream().map(InstrumentResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/instruments/{symbol}")
  public ResponseEntity<InstrumentResponse> getBySymbol(@PathVariable("symbol") String symbol) {
    return ResponseEntity.ok(InstrumentResponse.from(instrumentConfigService.findBySymbol(symbol)));
  }

  @PutMapping("/admin/instruments/{symbol}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<InstrumentResponse> upsert(
      @PathVariable("symbol") String symbol,
      @Valid @RequestBody UpsertInstrumentRequest request) {
    return ResponseEntity.ok(
        InstrumentResponse.from(
            instrumentConfigService.upsert(symbol, request.status(), request.referencePrice())));
  }

  @DeleteMapping("/admin/instruments/{symbol}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<InstrumentResponse> disable(@PathVariable("symbol") String symbol) {
    return ResponseEntity.ok(InstrumentResponse.from(instrumentConfigService.disable(symbol)));
  }
}
