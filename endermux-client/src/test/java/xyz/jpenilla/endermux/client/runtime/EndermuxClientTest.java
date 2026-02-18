package xyz.jpenilla.endermux.client.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndermuxClientTest {
  @Test
  void shouldQuitClientAlwaysForUserEof() {
    final RemoteConsoleSession.SessionOutcome outcome = outcome(
      true,
      RemoteConsoleSession.DisconnectReason.USER_EOF
    );
    assertTrue(EndermuxClient.shouldQuitClient(outcome, false));
    assertTrue(EndermuxClient.shouldQuitClient(outcome, true));
  }

  @Test
  void shouldQuitClientForUnrecoverableHandshakeByDefault() {
    final RemoteConsoleSession.SessionOutcome outcome = outcome(
      true,
      RemoteConsoleSession.DisconnectReason.UNRECOVERABLE_HANDSHAKE_FAILURE
    );
    assertTrue(EndermuxClient.shouldQuitClient(outcome, false));
  }

  @Test
  void shouldContinueForUnrecoverableHandshakeWhenIgnored() {
    final RemoteConsoleSession.SessionOutcome outcome = outcome(
      true,
      RemoteConsoleSession.DisconnectReason.UNRECOVERABLE_HANDSHAKE_FAILURE
    );
    assertFalse(EndermuxClient.shouldQuitClient(outcome, true));
  }

  @Test
  void shouldKeepRunningForGenericConnectionFailure() {
    final RemoteConsoleSession.SessionOutcome outcome = outcome(
      false,
      RemoteConsoleSession.DisconnectReason.GENERIC_CONNECTION_ERROR
    );
    assertFalse(EndermuxClient.shouldQuitClient(outcome, false));
    assertFalse(EndermuxClient.shouldQuitClient(outcome, true));
  }

  private static RemoteConsoleSession.SessionOutcome outcome(
    final boolean quitClient,
    final RemoteConsoleSession.DisconnectReason reason
  ) {
    return new RemoteConsoleSession.SessionOutcome(false, quitClient, reason);
  }
}
