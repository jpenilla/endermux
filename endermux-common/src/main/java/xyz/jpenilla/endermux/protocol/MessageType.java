package xyz.jpenilla.endermux.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MessageType {
  private static final Map<String, MessageType> ID_TO_TYPE = new HashMap<>();
  private static final Map<Class<? extends MessagePayload>, MessageType> PAYLOAD_TO_TYPE = new HashMap<>();
  private static final List<MessageType> ALL = new ArrayList<>();

  // ==============================================
  // Baseline message types, tied to transportEpoch
  // ==============================================

  // Handshake
  public static final MessageType HELLO = clientRequest("HELLO", Payloads.Hello.class);
  public static final MessageType WELCOME = serverMessage("WELCOME", Payloads.Welcome.class);
  public static final MessageType REJECT = serverMessage("REJECT", Payloads.Reject.class);

  // Session liveness and generic failures
  public static final MessageType PING = clientRequest("PING", Payloads.Ping.class);
  public static final MessageType PONG = serverMessage("PONG", Payloads.Pong.class);
  public static final MessageType ERROR = serverMessage("ERROR", Payloads.Error.class);

  // ==============================
  // Capability-based message types
  // ==============================

  // Interactivity state
  public static final MessageType INTERACTIVITY_STATUS = serverMessage(
    "INTERACTIVITY_STATUS",
    Payloads.InteractivityStatus.class,
    ProtocolCapabilities.INTERACTIVITY_STATUS
  );

  // Completions
  public static final MessageType COMPLETION_REQUEST = clientRequest(
    "COMPLETION_REQUEST",
    Payloads.CompletionRequest.class,
    ProtocolCapabilities.COMPLETION,
    true
  );
  public static final MessageType COMPLETION_RESPONSE = serverMessage(
    "COMPLETION_RESPONSE",
    Payloads.CompletionResponse.class,
    ProtocolCapabilities.COMPLETION
  );

  // Syntax highlighting
  public static final MessageType SYNTAX_HIGHLIGHT_REQUEST = clientRequest(
    "SYNTAX_HIGHLIGHT_REQUEST",
    Payloads.SyntaxHighlightRequest.class,
    ProtocolCapabilities.SYNTAX_HIGHLIGHT,
    true
  );
  public static final MessageType SYNTAX_HIGHLIGHT_RESPONSE = serverMessage(
    "SYNTAX_HIGHLIGHT_RESPONSE",
    Payloads.SyntaxHighlightResponse.class,
    ProtocolCapabilities.SYNTAX_HIGHLIGHT
  );

  // Parsing
  public static final MessageType PARSE_REQUEST = clientRequest(
    "PARSE_REQUEST",
    Payloads.ParseRequest.class,
    ProtocolCapabilities.PARSE,
    true
  );
  public static final MessageType PARSE_RESPONSE = serverMessage(
    "PARSE_RESPONSE",
    Payloads.ParseResponse.class,
    ProtocolCapabilities.PARSE
  );

  // Command execution
  public static final MessageType COMMAND_EXECUTE = clientMessage(
    "COMMAND_EXECUTE",
    Payloads.CommandExecute.class,
    ProtocolCapabilities.COMMAND_EXECUTE,
    true
  );

  // Log forwarding
  public static final MessageType LOG_SUBSCRIBE = clientMessage(
    "LOG_SUBSCRIBE",
    Payloads.LogSubscribe.class,
    ProtocolCapabilities.LOG_FORWARD,
    false
  );
  public static final MessageType LOG_FORWARD = serverMessage(
    "LOG_FORWARD",
    Payloads.LogForward.class,
    ProtocolCapabilities.LOG_FORWARD
  );

  private final String id;
  private final Direction direction;
  private final boolean requestIdRequired;
  private final Class<? extends MessagePayload> payloadType;
  private final @Nullable String capability;
  private final boolean interactivityRequired;

  private MessageType(
    final String id,
    final Direction direction,
    final boolean requestIdRequired,
    final Class<? extends MessagePayload> payloadType,
    final @Nullable String capability,
    final boolean interactivityRequired
  ) {
    this.id = id;
    this.direction = direction;
    this.requestIdRequired = requestIdRequired;
    this.payloadType = payloadType;
    this.capability = capability;
    this.interactivityRequired = interactivityRequired;
  }

  private static MessageType clientRequest(final String name, final Class<? extends MessagePayload> payloadType) {
    return register(new MessageType(name, Direction.CLIENT_TO_SERVER, true, payloadType, null, false));
  }

  private static MessageType serverMessage(final String name, final Class<? extends MessagePayload> payloadType) {
    return register(new MessageType(name, Direction.SERVER_TO_CLIENT, false, payloadType, null, false));
  }

  private static MessageType clientRequest(
    final String name,
    final Class<? extends MessagePayload> payloadType,
    final String capability,
    final boolean interactivityRequired
  ) {
    return register(new MessageType(name, Direction.CLIENT_TO_SERVER, true, payloadType, capability, interactivityRequired));
  }

  private static MessageType serverMessage(
    final String name,
    final Class<? extends MessagePayload> payloadType,
    final String capability
  ) {
    return register(new MessageType(name, Direction.SERVER_TO_CLIENT, false, payloadType, capability, false));
  }

  private static MessageType clientMessage(
    final String name,
    final Class<? extends MessagePayload> payloadType,
    final String capability,
    final boolean interactivityRequired
  ) {
    return register(new MessageType(name, Direction.CLIENT_TO_SERVER, false, payloadType, capability, interactivityRequired));
  }

  private static MessageType register(final MessageType type) {
    final MessageType existingById = ID_TO_TYPE.put(type.id, type);
    if (existingById != null) {
      throw new IllegalStateException("Duplicate message type id: " + type.id);
    }

    final MessageType existingByPayload = PAYLOAD_TO_TYPE.put(type.payloadType, type);
    if (existingByPayload != null) {
      throw new IllegalStateException("Duplicate payload type mapping for " + type.payloadType.getName());
    }

    ALL.add(type);
    return type;
  }

  public String id() {
    return this.id;
  }

  public Direction direction() {
    return this.direction;
  }

  public Class<? extends MessagePayload> payloadType() {
    return this.payloadType;
  }

  public @Nullable String capability() {
    return this.capability;
  }

  public boolean requiresInteractivity() {
    return this.interactivityRequired;
  }

  public boolean requestIdRequired() {
    return this.requestIdRequired;
  }

  public boolean isClientToServer() {
    return this.direction == Direction.CLIENT_TO_SERVER;
  }

  public boolean isServerToClient() {
    return this.direction == Direction.SERVER_TO_CLIENT;
  }

  public static List<MessageType> allTypes() {
    return Collections.unmodifiableList(ALL);
  }

  public static @Nullable MessageType findById(final String id) {
    return ID_TO_TYPE.get(id);
  }

  public static MessageType byIdOrThrow(final String id) {
    final @Nullable MessageType type = findById(id);
    if (type == null) {
      throw new IllegalArgumentException("No message type with id '" + id + "'");
    }
    return type;
  }

  public static @Nullable MessageType findByPayloadClass(final Class<? extends MessagePayload> payloadType) {
    return PAYLOAD_TO_TYPE.get(payloadType);
  }

  public static MessageType serverTypeForPayload(final MessagePayload payload) {
    final MessageType type = findByPayloadClass(payload.getClass());
    if (type == null) {
      throw new IllegalArgumentException("Unknown payload type: " + payload.getClass().getSimpleName());
    }
    if (!type.isServerToClient()) {
      throw new IllegalArgumentException("Cannot send request payload as response: " + payload.getClass().getSimpleName());
    }
    return type;
  }

  @Override
  public String toString() {
    return this.id;
  }

  public enum Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
  }
}
