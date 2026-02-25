package com.tradingplatform.tradingapi.connector;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ConnectorReplayService {
  private static final Logger log = LoggerFactory.getLogger(ConnectorReplayService.class);
  public static final String CONNECTOR_NAME_BINANCE_SPOT = "binance-spot";
  private static final String DEFAULT_REASON = "manual_replay";
  private static final int MAX_REASON_LENGTH = 200;

  private final ConnectorReplayRequestRepository replayRequestRepository;
  private final Clock clock;

  @Autowired
  public ConnectorReplayService(ConnectorReplayRequestRepository replayRequestRepository) {
    this(replayRequestRepository, Clock.systemUTC());
  }

  ConnectorReplayService(ConnectorReplayRequestRepository replayRequestRepository, Clock clock) {
    this.replayRequestRepository = replayRequestRepository;
    this.clock = clock;
  }

  public ConnectorReplaySubmission enqueueManualReplay(
      String connector, String reason, Authentication authentication) {
    String connectorName = normalizedConnector(connector);
    String replayReason = normalizedReason(reason);
    String requestedBy = actor(authentication);
    Instant requestedAt = clock.instant();
    ConnectorReplaySubmission submission =
        replayRequestRepository.createManualReplayRequest(
        connectorName, replayReason, requestedBy, requestedAt);
    log.info(
        "Connector replay request accepted connector={} requestId={} requestedBy={} reason={}",
        connectorName,
        submission.requestId(),
        requestedBy,
        replayReason);
    return submission;
  }

  private static String normalizedConnector(String connector) {
    String candidate = connector == null ? "" : connector.trim();
    if (candidate.isEmpty()) {
      return CONNECTOR_NAME_BINANCE_SPOT;
    }
    if (CONNECTOR_NAME_BINANCE_SPOT.equals(candidate)) {
      return candidate;
    }
    throw new IllegalArgumentException("Unsupported connector: " + candidate);
  }

  private static String normalizedReason(String reason) {
    String candidate = reason == null ? "" : reason.replaceAll("\\s+", " ").trim();
    if (candidate.isEmpty()) {
      return DEFAULT_REASON;
    }
    if (candidate.length() > MAX_REASON_LENGTH) {
      throw new IllegalArgumentException(
          "Replay reason exceeds max length " + MAX_REASON_LENGTH + " characters");
    }
    return candidate;
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
