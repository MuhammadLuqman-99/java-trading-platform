package com.tradingplatform.tradingapi.api;

import com.tradingplatform.tradingapi.connector.ConnectorReplayService;
import com.tradingplatform.tradingapi.connector.ConnectorReplaySubmission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/admin/connector")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConnectorReplayController {
  private final ConnectorReplayService connectorReplayService;

  public AdminConnectorReplayController(ConnectorReplayService connectorReplayService) {
    this.connectorReplayService = connectorReplayService;
  }

  @PostMapping("/catch-up/replay")
  public ResponseEntity<AdminConnectorReplayResponse> replayCatchUp(
      @Valid @RequestBody(required = false) AdminConnectorReplayRequest request,
      Authentication authentication) {
    String connector = request == null ? null : request.connector();
    String reason = request == null ? null : request.reason();
    ConnectorReplaySubmission submission;
    try {
      submission = connectorReplayService.enqueueManualReplay(connector, reason, authentication);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
    return ResponseEntity.accepted().body(AdminConnectorReplayResponse.from(submission));
  }
}
