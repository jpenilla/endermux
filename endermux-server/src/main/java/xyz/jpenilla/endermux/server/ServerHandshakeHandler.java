package xyz.jpenilla.endermux.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.protocol.CapabilityVersionRange;
import xyz.jpenilla.endermux.protocol.HandshakeRejectReasons;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.ProtocolCapabilities;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;

@NullMarked
final class ServerHandshakeHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ServerHandshakeHandler.class);

  private final Map<String, CapabilityVersionRange> supportedCapabilities;

  ServerHandshakeHandler(final Map<String, CapabilityVersionRange> supportedCapabilities) {
    this.supportedCapabilities = supportedCapabilities;
  }

  @Nullable HandshakeResult performHandshake(final ClientEndpoint connection) throws IOException {
    final Message<?> message = connection.readInitialMessage(SocketProtocolConstants.HANDSHAKE_TIMEOUT_MS);
    final @Nullable String requestId = message.requestId();
    if (requestId == null) {
      this.sendHandshakeReject(
        connection,
        null,
        HandshakeRejectReasons.MISSING_REQUEST_ID,
        "Missing requestId",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }
    if (message.type() != MessageType.HELLO || !(message.payload() instanceof Payloads.Hello hello)) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.EXPECTED_HELLO,
        "Expected HELLO",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    final CapabilityVersionRange transportEpochRange = hello.transportEpochRange();
    if (transportEpochRange == null || !transportEpochRange.isValid()) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.INVALID_TRANSPORT_EPOCH_RANGE,
        "Invalid transport epoch range",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    if (!transportEpochRange.includes(SocketProtocolConstants.TRANSPORT_EPOCH)) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.UNSUPPORTED_TRANSPORT_EPOCH,
        "Unsupported transport epoch",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    if (hello.colorLevel() == null) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.MISSING_COLOR_LEVEL,
        "Missing color level",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    if (hello.capabilities() == null || hello.requiredCapabilities() == null) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.MISSING_CAPABILITY_NEGOTIATION_DATA,
        "Missing capability negotiation data",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    final @Nullable Map<String, Integer> selectedCapabilities = this.negotiateCapabilities(hello.capabilities());
    if (selectedCapabilities == null) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.INVALID_CAPABILITY_VERSION_RANGE,
        "Invalid capability version range",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    final @Nullable Set<String> missingRequiredCapabilities = this.missingRequiredCapabilities(
      selectedCapabilities,
      hello.requiredCapabilities()
    );
    if (missingRequiredCapabilities == null) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.INVALID_REQUIRED_CAPABILITY_DECLARATION,
        "Invalid required capability declaration",
        SocketProtocolConstants.TRANSPORT_EPOCH,
        Set.of()
      );
      return null;
    }

    if (!missingRequiredCapabilities.isEmpty()) {
      this.sendHandshakeReject(
        connection,
        requestId,
        HandshakeRejectReasons.MISSING_REQUIRED_CAPABILITIES,
        "Missing required capabilities",
        null,
        missingRequiredCapabilities
      );
      return null;
    }

    final Map<String, Integer> selected = Map.copyOf(selectedCapabilities);
    this.sendHandshakeResponse(connection, requestId, new Payloads.Welcome(SocketProtocolConstants.TRANSPORT_EPOCH, selected));
    return new HandshakeResult(hello, selected);
  }

  private @Nullable Map<String, Integer> negotiateCapabilities(final Map<String, CapabilityVersionRange> clientCapabilities) {
    final Map<String, Integer> selected = new HashMap<>();
    for (final Map.Entry<String, CapabilityVersionRange> entry : clientCapabilities.entrySet()) {
      final String name = entry.getKey();
      final CapabilityVersionRange clientRange = entry.getValue();
      final CapabilityVersionRange serverRange = this.supportedCapabilities.get(name);
      if (name == null || clientRange == null || !clientRange.isValid()) {
        return null;
      }
      if (serverRange == null) {
        continue;
      }
      final @Nullable Integer negotiatedVersion = ProtocolCapabilities.negotiateHighestCommonVersion(clientRange, serverRange);
      if (negotiatedVersion != null) {
        selected.put(name, negotiatedVersion);
      }
    }
    return selected;
  }

  private @Nullable Set<String> missingRequiredCapabilities(
    final Map<String, Integer> selectedCapabilities,
    final Set<String> requiredCapabilities
  ) {
    final Set<String> missing = new HashSet<>();
    for (final String name : requiredCapabilities) {
      if (name == null) {
        return null;
      }
      final Integer selectedVersion = selectedCapabilities.get(name);
      if (selectedVersion == null) {
        missing.add(name);
      }
    }
    return missing;
  }

  private void sendHandshakeReject(
    final ClientEndpoint connection,
    final @Nullable String requestId,
    final String reason,
    final String message,
    final @Nullable Integer expectedTransportEpoch,
    final Set<String> missingRequiredCapabilities
  ) {
    this.sendHandshakeResponse(
      connection,
      requestId,
      new Payloads.Reject(reason, message, expectedTransportEpoch, missingRequiredCapabilities)
    );
  }

  private void sendHandshakeResponse(
    final ClientEndpoint connection,
    final @Nullable String requestId,
    final MessagePayload payload
  ) {
    final MessageType type = MessageType.serverTypeForPayload(payload);
    final Message<?> message = requestId == null
      ? Message.unsolicited(type, payload)
      : Message.response(requestId, type, payload);
    if (!connection.sendNow(message)) {
      LOGGER.debug("Failed to send handshake response to client");
    }
  }

  record HandshakeResult(Payloads.Hello hello, Map<String, Integer> selectedCapabilities) {
  }
}
