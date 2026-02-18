package xyz.jpenilla.endermux.client.transport;

import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MissingRequiredCapabilitiesException extends HandshakeFatalException {
  @java.io.Serial
  private static final long serialVersionUID = 1L;

  private final String reason;
  @SuppressWarnings("serial")
  private final Set<String> missingRequiredCapabilities;

  public MissingRequiredCapabilitiesException(
    final String reason,
    final Set<String> missingRequiredCapabilities
  ) {
    super(reason);
    this.reason = reason;
    this.missingRequiredCapabilities = Set.copyOf(missingRequiredCapabilities);
  }

  public @Nullable String reason() {
    return this.reason;
  }

  public Set<String> missingRequiredCapabilities() {
    return this.missingRequiredCapabilities;
  }

  @Override
  public String userFacingMessage() {
    final StringBuilder message = new StringBuilder();
    message.append("Protocol capability mismatch: missing required capabilities ");
    message.append(this.missingRequiredCapabilities.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("<none>"));
    if (!this.reason.isBlank()) {
      message.append(". Reason: ");
      message.append(this.reason);
    }
    message.append(". Please update client/server to compatible versions.");
    return message.toString();
  }
}
