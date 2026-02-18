package xyz.jpenilla.endermux.client.transport;

import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class HandshakeFatalException extends Exception {
  @java.io.Serial
  private static final long serialVersionUID = 1L;

  protected HandshakeFatalException(final String reason) {
    super(reason);
  }

  public abstract String userFacingMessage();
}
