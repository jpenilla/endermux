package xyz.jpenilla.endermux.client.transport;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class InvalidHandshakeResponseException extends HandshakeFatalException {
  @java.io.Serial
  private static final long serialVersionUID = 1L;

  public InvalidHandshakeResponseException(final String reason) {
    super(reason);
  }

  @Override
  public String userFacingMessage() {
    final StringBuilder message = new StringBuilder();
    message.append("Handshake failed: invalid response from server");
    if (this.getMessage() != null && !this.getMessage().isBlank()) {
      message.append(" (");
      message.append(this.getMessage());
      message.append(")");
    }
    message.append(". Please update client/server to compatible versions.");
    return message.toString();
  }
}
