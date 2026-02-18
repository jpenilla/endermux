package xyz.jpenilla.endermux.client.transport;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class UnknownRejectReasonException extends HandshakeFatalException {
  @java.io.Serial
  private static final long serialVersionUID = 1L;

  private final String rejectReason;
  private final String rejectMessage;

  public UnknownRejectReasonException(final String rejectReason, final String rejectMessage) {
    super(rejectMessage);
    this.rejectReason = rejectReason;
    this.rejectMessage = rejectMessage;
  }

  public String rejectReason() {
    return this.rejectReason;
  }

  @Override
  public String userFacingMessage() {
    final StringBuilder message = new StringBuilder();
    message.append("Handshake rejected with reason '");
    message.append(this.rejectReason);
    message.append("'");
    if (!this.rejectMessage.isBlank()) {
      message.append(": ");
      message.append(this.rejectMessage);
    }
    message.append(". Please update client/server to compatible versions.");
    return message.toString();
  }
}
