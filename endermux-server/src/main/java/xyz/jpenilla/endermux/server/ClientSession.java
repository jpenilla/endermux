package xyz.jpenilla.endermux.server;

import java.util.Map;
import java.util.function.Consumer;
import net.kyori.ansi.ColorLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jpenilla.endermux.ansi.ColorLevelContext;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.server.handlers.HandlerRegistry;
import xyz.jpenilla.endermux.server.handlers.ResponseContext;

@NullMarked
public final class ClientSession implements Consumer<Message<?>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientSession.class);

  private final ClientEndpoint connection;
  private final HandlerRegistry handlerRegistry;
  private volatile boolean logReady = false;
  private volatile boolean interactivityAvailable;
  private volatile ColorLevel colorLevel = ColorLevel.NONE;
  private volatile Map<String, Integer> negotiatedCapabilities = Map.of();

  public ClientSession(
    final ClientEndpoint connection,
    final HandlerRegistry handlerRegistry,
    final boolean interactivityAvailable
  ) {
    this.connection = connection;
    this.handlerRegistry = handlerRegistry;
    this.interactivityAvailable = interactivityAvailable;
  }

  public void initialize() {
    if (this.supportsCapability(MessageType.INTERACTIVITY_STATUS)) {
      this.send(Message.unsolicited(
        MessageType.INTERACTIVITY_STATUS,
        new Payloads.InteractivityStatus(this.interactivityAvailable)
      ));
    }
  }

  @Override
  public void accept(final Message<?> message) {
    final ResponseContext ctx = new ImmutableResponseContext(message.requestId());

    if (message.requestId() == null && message.type().requestIdRequired()) {
      ctx.error("Missing requestId for message type: " + message.type());
      return;
    }

    if (message.type() == MessageType.PING) {
      this.handlePing(ctx);
      return;
    }

    final @Nullable String capability = message.type().capability();
    if (capability != null && !this.supportsCapability(capability)) {
      ctx.error("Capability not negotiated: " + capability);
      return;
    }

    if (message.type() == MessageType.LOG_SUBSCRIBE) {
      if (!this.supportsCapability(MessageType.LOG_FORWARD)) {
        ctx.error("Capability not negotiated: " + MessageType.LOG_FORWARD.capability());
        return;
      }
      this.logReady = true;
      return;
    }

    if (message.type().direction() != MessageType.Direction.CLIENT_TO_SERVER) {
      ctx.error("Invalid message direction: " + message.type());
      return;
    }

    if (!this.interactivityAvailable && message.type().requiresInteractivity()) {
      ctx.error("Interactivity is currently unavailable");
      return;
    }

    final boolean handled;
    try (final ColorLevelContext.Scope _ = ColorLevelContext.push(this.colorLevel)) {
      handled = this.handlerRegistry.handle(
        message.type(),
        message.payload(),
        ctx
      );
    }

    if (!handled) {
      ctx.error("Unknown message type: " + message.type());
    }
  }

  public boolean isLogReady() {
    return this.logReady && this.supportsCapability(MessageType.LOG_FORWARD);
  }

  public ColorLevel colorLevel() {
    return this.colorLevel;
  }

  void setColorLevel(final ColorLevel colorLevel) {
    this.colorLevel = colorLevel;
  }

  void setNegotiatedCapabilities(final Map<String, Integer> negotiatedCapabilities) {
    this.negotiatedCapabilities = Map.copyOf(negotiatedCapabilities);
  }

  void setInteractivityAvailable(final boolean available) {
    this.interactivityAvailable = available;
    if (this.supportsCapability(MessageType.INTERACTIVITY_STATUS)) {
      this.send(Message.unsolicited(
        MessageType.INTERACTIVITY_STATUS,
        new Payloads.InteractivityStatus(available)
      ));
    }
  }

  private boolean supportsCapability(final String capability) {
    return this.negotiatedCapabilities.containsKey(capability);
  }

  private boolean supportsCapability(final MessageType type) {
    final @Nullable String capability = type.capability();
    return capability == null || this.supportsCapability(capability);
  }

  private void handlePing(final ResponseContext ctx) {
    ctx.reply(new Payloads.Pong());
  }

  private void send(final Message<?> message) {
    if (!this.connection.send(message)) {
      LOGGER.debug("Failed to send message to client");
    }
  }

  private final class ImmutableResponseContext implements ResponseContext {
    private final @Nullable String requestId;

    ImmutableResponseContext(final @Nullable String requestId) {
      this.requestId = requestId;
    }

    @Override
    public @Nullable String requestId() {
      return this.requestId;
    }

    @Override
    public void reply(final MessagePayload payload) {
      ClientSession.this.send(this.buildResponse(payload));
    }

    @Override
    public void error(final String message) {
      error(message, null);
    }

    @Override
    public void error(final String message, final @Nullable String details) {
      ClientSession.this.send(this.buildError(message, details));
    }

    private Message<?> buildResponse(final MessagePayload payload) {
      final MessageType responseType = MessageType.serverTypeForPayload(payload);
      return this.requestId != null
        ? Message.response(this.requestId, responseType, payload)
        : Message.unsolicited(responseType, payload);
    }

    private Message<Payloads.Error> buildError(final String message, final @Nullable String details) {
      return this.requestId != null
        ? Message.response(this.requestId, MessageType.ERROR, new Payloads.Error(message, details))
        : Message.unsolicited(MessageType.ERROR, new Payloads.Error(message, details));
    }
  }
}
