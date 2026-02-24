package xyz.jpenilla.endermux.client.runtime;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.client.transport.HandshakeFatalException;
import xyz.jpenilla.endermux.client.transport.SocketTransport;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessagePayload;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public final class RemoteConsoleSession {
  private static final String TERMINAL_PROMPT = "> ";
  private static final String DISCONNECT_HINT_MESSAGE = "Press Ctrl+D to disconnect from console.";
  private static final long SOCKET_POLL_INTERVAL_MS = 500;
  private static final ComponentLogger LOGGER = ComponentLogger.logger(RemoteConsoleSession.class);

  private final String socketPath;
  private final TerminalRuntimeContext terminalContext;
  private final ExecutorService logExecutor;
  private final BooleanSupplier shutdownRequested;

  private volatile @Nullable SocketTransport socketClient;
  private volatile boolean interactiveAvailable;
  private volatile boolean suppressNextInterruptHint;
  private volatile @Nullable LineReader lineReader;

  RemoteConsoleSession(
    final String socketPath,
    final TerminalRuntimeContext terminalContext,
    final ExecutorService logExecutor,
    final BooleanSupplier shutdownRequested
  ) {
    this.socketPath = socketPath;
    this.terminalContext = terminalContext;
    this.logExecutor = logExecutor;
    this.shutdownRequested = shutdownRequested;
  }

  SessionOutcome run() {
    boolean didConnect = false;
    try {
      this.socketClient = new SocketTransport(this.socketPath);
      this.interactiveAvailable = false;

      final SocketTransport client = this.socketClient;
      client.setDisconnectCallback(this::onDisconnect);
      client.setMessageHandler(this::handleMessage);
      client.connect();
      didConnect = true;

      LOGGER.info(text()
        .append(text("Connected to Endermux server via socket: ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
        .append(text(this.socketPath))
        .build());

      this.lineReader = this.terminalContext.createLineReader(this, client);
      TerminalOutput.setLineReader(this.lineReader);

      client.sendMessage(Message.unsolicited(MessageType.LOG_SUBSCRIBE, new Payloads.LogSubscribe()));

      final AcceptInputResult acceptInputResult = this.acceptInput();

      return new SessionOutcome(
        didConnect,
        acceptInputResult == AcceptInputResult.USER_QUIT ? DisconnectReason.USER_EOF : DisconnectReason.CONNECTION_CLOSED
      );
    } catch (final HandshakeFatalException e) {
      final String message = e.userFacingMessage();
      LOGGER.error(message);
      return new SessionOutcome(didConnect, DisconnectReason.UNRECOVERABLE_HANDSHAKE_FAILURE);
    } catch (final Exception e) {
      LOGGER.debug("Connection failure", e);
      LOGGER.error("Connection failed: {}", e.getMessage());
      return new SessionOutcome(didConnect, DisconnectReason.GENERIC_CONNECTION_ERROR);
    } finally {
      this.cleanup();
    }
  }

  void disconnect() {
    final SocketTransport client = this.socketClient;
    if (client != null) {
      client.disconnect();
    }
  }

  boolean isConnected() {
    final SocketTransport client = this.socketClient;
    return client != null && client.isConnected();
  }

  private void onDisconnect() {
    this.terminalContext.interruptActiveReader(this.lineReader);
  }

  private void cleanup() {
    final SocketTransport client = this.socketClient;
    if (client != null) {
      client.disconnect();
    }
    this.socketClient = null;
    this.lineReader = null;
    TerminalOutput.setLineReader(null);
  }

  private enum AcceptInputResult {
    USER_QUIT,
    CONNECTION_CLOSED
  }

  private AcceptInputResult acceptInput() {
    if (this.terminalContext.isDumbTerminal()) {
      return this.acceptInputDumb();
    }
    final LineReader sessionReader = this.lineReader;
    if (sessionReader == null) {
      throw new IllegalStateException("LineReader is not initialized");
    }
    return this.acceptInputInteractive(sessionReader);
  }

  private AcceptInputResult acceptInputInteractive(final LineReader sessionReader) {
    while (true) {
      final SocketTransport client = this.connectedClient();
      if (client == null) {
        return AcceptInputResult.CONNECTION_CLOSED;
      }
      this.updateReaderMode(sessionReader);

      final String input;
      try {
        input = sessionReader.readLine(getTerminalPrompt(this.interactiveAvailable));
      } catch (final UserInterruptException e) {
        this.printDisconnectHint();
        continue;
      } catch (final EndOfFileException e) {
        return AcceptInputResult.USER_QUIT;
      } catch (final IOError e) {
        LOGGER.warn("Terminal IO error while reading input", e);
        continue;
      }

      if (input == null) {
        return AcceptInputResult.USER_QUIT;
      }

      final String trimmedInput = input.trim();
      if (trimmedInput.isEmpty()) {
        continue;
      }
      if (!this.interactiveAvailable) {
        LOGGER.debug("Ignoring input while interactivity is unavailable");
        continue;
      }
      this.sendCommand(client, trimmedInput);
    }
  }

  private AcceptInputResult acceptInputDumb() {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      final SocketTransport client = this.connectedClient();
      if (client == null) {
        return AcceptInputResult.CONNECTION_CLOSED;
      }

      final String input;
      try {
        input = this.readInputLine(reader);
      } catch (final IOException e) {
        LOGGER.error("Error reading stdin: {}", e.getMessage());
        LOGGER.debug("Error reading stdin", e);
        return AcceptInputResult.CONNECTION_CLOSED;
      }

      if (input == null) {
        if (this.connectedClient() == null || this.shutdownRequested.getAsBoolean()) {
          return AcceptInputResult.CONNECTION_CLOSED;
        }
        if (!this.terminalContext.hasConsoleInput()) {
          this.sleepForInput();
          continue;
        }
        return AcceptInputResult.USER_QUIT;
      }

      final String trimmedInput = input.trim();
      if (trimmedInput.isEmpty()) {
        continue;
      }
      if (!this.interactiveAvailable) {
        LOGGER.debug("Ignoring input while interactivity is unavailable");
        continue;
      }
      this.sendCommand(client, trimmedInput);
    }
  }

  private void sleepForInput() {
    try {
      Thread.sleep(SOCKET_POLL_INTERVAL_MS);
    } catch (final InterruptedException e) {
      if (!this.shutdownRequested.getAsBoolean()) {
        LOGGER.debug("Interrupted while waiting for stdin input", e);
      }
    }
  }

  private @Nullable String readInputLine(final BufferedReader reader) throws IOException {
    while (true) {
      if (this.shutdownRequested.getAsBoolean() || this.connectedClient() == null) {
        return null;
      }
      if (reader.ready()) {
        return reader.readLine();
      }
      this.sleepForInput();
      if (this.shutdownRequested.getAsBoolean()) {
        return null;
      }
    }
  }

  private void handleMessage(final Message<? extends MessagePayload> message) {
    if (this.connectedClient() == null) {
      LOGGER.warn("Not connected to server");
      return;
    }

    final MessageType type = message.type();
    if (type == MessageType.LOG_FORWARD && message.payload() instanceof Payloads.LogForward logForward) {
      this.logExecutor.execute(() -> this.processLogMessage(logForward));
      return;
    }

    if (type == MessageType.ERROR && message.payload() instanceof Payloads.Error(String errorMessage, String details)) {
      this.printError(errorMessage, details);
      return;
    }

    if (type == MessageType.INTERACTIVITY_STATUS
      && message.payload() instanceof Payloads.InteractivityStatus(boolean available)) {
      final boolean changed = this.interactiveAvailable != available;
      this.interactiveAvailable = available;
      if (changed) {
        this.suppressNextInterruptHint = this.terminalContext.interruptActiveReader(this.lineReader);
      }
    }
  }

  private void updateReaderMode(final LineReader sessionReader) {
    if (this.interactiveAvailable) {
      sessionReader.unsetOpt(LineReader.Option.ERASE_LINE_ON_FINISH);
    } else {
      sessionReader.setOpt(LineReader.Option.ERASE_LINE_ON_FINISH);
    }
  }

  private void processLogMessage(final Payloads.LogForward logForward) {
    this.printLogMessage(logForward.rendered());
  }

  private void printLogMessage(final String formattedMessage) {
    TerminalOutput.write(formattedMessage);
  }

  private void sendCommand(final SocketTransport client, final String input) {
    final Payloads.CommandExecute payload = new Payloads.CommandExecute(input);
    final Message<Payloads.CommandExecute> commandMessage = Message.unsolicited(MessageType.COMMAND_EXECUTE, payload);
    client.sendMessage(commandMessage);
  }

  private @Nullable SocketTransport connectedClient() {
    final SocketTransport client = this.socketClient;
    if (client == null || !client.isConnected()) {
      return null;
    }
    return client;
  }

  private boolean consumeSuppressedInterruptHint() {
    if (!this.suppressNextInterruptHint) {
      return false;
    }
    this.suppressNextInterruptHint = false;
    return true;
  }

  public void printDisconnectHint() {
    if (this.consumeSuppressedInterruptHint()) {
      return;
    }
    if (!this.isConnected()) {
      return;
    }
    LOGGER.info(DISCONNECT_HINT_MESSAGE);
  }

  private void printError(final String message, final @Nullable String details) {
    LOGGER.error("Error: {}", message);
    if (details != null) {
      LOGGER.error("Details: {}", details);
    }
  }

  private static String getTerminalPrompt(final boolean interactive) {
    return interactive ? TERMINAL_PROMPT : "";
  }

  enum DisconnectReason {
    USER_EOF(true),
    CONNECTION_CLOSED(false),
    UNRECOVERABLE_HANDSHAKE_FAILURE(true),
    GENERIC_CONNECTION_ERROR(false);

    private final boolean quitClientByDefault;

    DisconnectReason(final boolean quitClientByDefault) {
      this.quitClientByDefault = quitClientByDefault;
    }

    boolean quitClientByDefault() {
      return this.quitClientByDefault;
    }
  }

  record SessionOutcome(
    boolean didConnect,
    DisconnectReason disconnectReason
  ) {
  }
}
