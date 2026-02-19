package xyz.jpenilla.endermux.client.transport;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.ansi.ColorLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.protocol.CapabilityVersionRange;
import xyz.jpenilla.endermux.protocol.HandshakeRejectReasons;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;

@NullMarked
final class ClientHandshakeHandler {
  private final CapabilityVersionRange supportedTransportEpochRange;
  private final Map<String, CapabilityVersionRange> supportedCapabilities;
  private final Set<String> requiredCapabilities;

  ClientHandshakeHandler(
    final CapabilityVersionRange supportedTransportEpochRange,
    final Map<String, CapabilityVersionRange> supportedCapabilities,
    final Set<String> requiredCapabilities
  ) {
    if (!supportedTransportEpochRange.isValid()) {
      throw new IllegalArgumentException("supportedTransportEpochRange must be valid");
    }
    this.supportedTransportEpochRange = supportedTransportEpochRange;
    this.supportedCapabilities = supportedCapabilities;
    this.requiredCapabilities = requiredCapabilities;
  }

  Payloads.Hello createHelloPayload() {
    return new Payloads.Hello(
      this.supportedTransportEpochRange,
      ColorLevel.compute(),
      this.supportedCapabilities,
      this.requiredCapabilities
    );
  }

  Map<String, Integer> handleHandshakeResponse(
    final String expectedRequestId,
    final Message<?> response
  ) throws HandshakeFatalException {
    if (!expectedRequestId.equals(response.requestId())) {
      throw new InvalidHandshakeResponseException("requestId mismatch");
    }

    if (response.type() == MessageType.REJECT && response.payload() instanceof Payloads.Reject reject) {
      this.handleReject(reject);
    }

    if (response.type() != MessageType.WELCOME || !(response.payload() instanceof Payloads.Welcome welcome)) {
      throw new InvalidHandshakeResponseException("unexpected response type: " + response.type());
    }

    if (!this.supportedTransportEpochRange.includes(welcome.transportEpoch())) {
      throw new ProtocolMismatchException(
        "Unsupported transport epoch: " + welcome.transportEpoch(),
        welcome.transportEpoch(),
        SocketProtocolConstants.TRANSPORT_EPOCH
      );
    }

    if (welcome.selectedCapabilities() == null) {
      throw new InvalidHandshakeResponseException("missing selected capabilities");
    }

    final Map<String, Integer> selectedCapabilities = new HashMap<>(welcome.selectedCapabilities());
    final @Nullable String validationError = this.validateSelectedCapabilities(selectedCapabilities);
    if (validationError != null) {
      throw new InvalidHandshakeResponseException(validationError);
    }

    return Map.copyOf(selectedCapabilities);
  }

  private void handleReject(final Payloads.Reject reject) throws HandshakeFatalException {
    final String rejectReason = reject.reason();
    final String reason = rejectReason == null || rejectReason.isBlank()
      ? "<missing>"
      : rejectReason;
    final String message = reject.message() == null
      ? ""
      : reject.message();

    switch (reason) {
      case HandshakeRejectReasons.UNSUPPORTED_TRANSPORT_EPOCH -> {
        final Integer expectedTransportEpoch = reject.expectedTransportEpoch();
        if (expectedTransportEpoch == null) {
          throw new UnknownRejectReasonException(reason, message);
        }
        throw new ProtocolMismatchException(
          message,
          expectedTransportEpoch,
          SocketProtocolConstants.TRANSPORT_EPOCH
        );
      }
      case HandshakeRejectReasons.MISSING_REQUIRED_CAPABILITIES -> {
        final Set<String> missingRequired = reject.missingRequiredCapabilities();
        if (missingRequired == null || missingRequired.isEmpty()) {
          throw new UnknownRejectReasonException(reason, message);
        }
        throw new MissingRequiredCapabilitiesException(message, missingRequired);
      }
      default -> throw new UnknownRejectReasonException(reason, message);
    }
  }

  private @Nullable String validateSelectedCapabilities(
    final Map<String, Integer> selectedCapabilities
  ) throws MissingRequiredCapabilitiesException {
    for (final Map.Entry<String, Integer> entry : selectedCapabilities.entrySet()) {
      final String name = entry.getKey();
      final Integer version = entry.getValue();
      if (name == null || version == null || version < 1) {
        return "Selected capabilities contain invalid entry";
      }

      final CapabilityVersionRange supportedRange = this.supportedCapabilities.get(name);
      if (supportedRange == null) {
        continue;
      }
      if (!supportedRange.includes(version)) {
        return "Server selected unsupported version " + version + " for capability '" + name + "'";
      }
    }

    final Set<String> missingRequired = new HashSet<>();
    for (final String required : this.requiredCapabilities) {
      final Integer selectedVersion = selectedCapabilities.get(required);
      if (selectedVersion == null) {
        missingRequired.add(required);
      }
    }
    if (!missingRequired.isEmpty()) {
      throw new MissingRequiredCapabilitiesException("Missing required capabilities", missingRequired);
    }

    return null;
  }
}
