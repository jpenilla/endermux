package xyz.jpenilla.endermux.client.transport;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ProtocolMismatchException extends HandshakeFatalException {
  @java.io.Serial
  private static final long serialVersionUID = 1L;

  private final String reason;
  private final int expectedTransportEpoch;
  private final int actualTransportEpoch;

  public ProtocolMismatchException(final String reason, final int expectedTransportEpoch, final int actualTransportEpoch) {
    super(reason);
    this.reason = reason;
    this.expectedTransportEpoch = expectedTransportEpoch;
    this.actualTransportEpoch = actualTransportEpoch;
  }

  public @Nullable String reason() {
    return this.reason;
  }

  public int expectedTransportEpoch() {
    return this.expectedTransportEpoch;
  }

  public int actualTransportEpoch() {
    return this.actualTransportEpoch;
  }

  @Override
  public String userFacingMessage() {
    final StringBuilder message = new StringBuilder();
    message.append("Transport epoch mismatch: server expects epoch ");
    message.append(this.expectedTransportEpoch);
    message.append(", client is epoch ");
    message.append(this.actualTransportEpoch);
    if (!this.reason.isBlank()) {
      message.append(". Reason: ");
      message.append(this.reason);
    }
    message.append(". Please update client/server to matching versions.");
    return message.toString();
  }
}
