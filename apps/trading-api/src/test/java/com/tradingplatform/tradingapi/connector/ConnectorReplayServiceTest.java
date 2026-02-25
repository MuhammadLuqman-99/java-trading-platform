package com.tradingplatform.tradingapi.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

class ConnectorReplayServiceTest {
  @Test
  void shouldDefaultConnectorReasonAndActor() {
    InMemoryReplayRequestRepository repository = new InMemoryReplayRequestRepository();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);
    ConnectorReplayService service = new ConnectorReplayService(repository, clock);

    ConnectorReplaySubmission submission = service.enqueueManualReplay(null, "   ", null);

    assertEquals("binance-spot", submission.connectorName());
    assertEquals("PENDING", submission.status());
    assertEquals(Instant.parse("2026-02-25T12:00:00Z"), submission.requestedAt());
    assertEquals("admin", submission.requestedBy());
    assertEquals("manual_replay", repository.lastReason);
    assertTrue(repository.lastConnectorName.equals("binance-spot"));
  }

  @Test
  void shouldUseJwtSubjectAsActor() {
    InMemoryReplayRequestRepository repository = new InMemoryReplayRequestRepository();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);
    ConnectorReplayService service = new ConnectorReplayService(repository, clock);
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "ops-admin").build();
    Authentication authentication = Mockito.mock(Authentication.class);
    Mockito.when(authentication.getPrincipal()).thenReturn(jwt);

    ConnectorReplaySubmission submission =
        service.enqueueManualReplay("binance-spot", "manual incident replay", authentication);

    assertEquals("ops-admin", submission.requestedBy());
    assertEquals("manual incident replay", repository.lastReason);
  }

  @Test
  void shouldRejectUnsupportedConnector() {
    InMemoryReplayRequestRepository repository = new InMemoryReplayRequestRepository();
    Clock clock = Clock.fixed(Instant.parse("2026-02-25T12:00:00Z"), ZoneOffset.UTC);
    ConnectorReplayService service = new ConnectorReplayService(repository, clock);

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.enqueueManualReplay("coinbase", "manual replay", null));

    assertTrue(error.getMessage().contains("Unsupported connector"));
  }

  private static final class InMemoryReplayRequestRepository
      implements ConnectorReplayRequestRepository {
    private String lastConnectorName;
    private String lastReason;
    private String lastRequestedBy;
    private Instant lastRequestedAt;
    private UUID lastId;

    @Override
    public ConnectorReplaySubmission createManualReplayRequest(
        String connectorName, String reason, String requestedBy, Instant requestedAt) {
      this.lastConnectorName = connectorName;
      this.lastReason = reason;
      this.lastRequestedBy = requestedBy;
      this.lastRequestedAt = requestedAt;
      this.lastId = UUID.randomUUID();
      return new ConnectorReplaySubmission(
          lastId, lastConnectorName, "PENDING", lastRequestedAt, lastRequestedBy);
    }
  }
}
