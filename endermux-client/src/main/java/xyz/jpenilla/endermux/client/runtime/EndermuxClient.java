package xyz.jpenilla.endermux.client.runtime;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.client.EndermuxCli;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public final class EndermuxClient {
  private static final long SOCKET_POLL_INTERVAL_MS = 500;
  private static final long INITIAL_RETRY_BACKOFF_MS = 1000L;
  private static final long MAX_RETRY_BACKOFF_MS = 60_000L;
  private static final ComponentLogger LOGGER = ComponentLogger.logger(EndermuxClient.class);

  private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
    final Thread thread = new Thread(r, "LogOutput");
    thread.setDaemon(true);
    return thread;
  });

  private volatile boolean shutdownRequested;
  private volatile @Nullable ExitReason exitReason;
  private @Nullable TerminalRuntimeContext terminalContext;
  private volatile @Nullable RemoteConsoleSession activeSession;

  public int run(final String socketPath, final boolean ignoreUnrecoverableHandshake) {
    this.terminalContext = TerminalRuntimeContext.create();

    LOGGER.info(EndermuxCli.VERSION_MESSAGE);

    try {
      this.registerSignalHandlers();
      int retryCount = 0;
      final SocketPathWatcher socketWatcher = new SocketPathWatcher(socketPath, SOCKET_POLL_INTERVAL_MS, LOGGER);
      while (true) {
        if (this.shutdownRequested) {
          break;
        }

        if (!socketWatcher.waitForSocket(() -> this.shutdownRequested)) {
          break;
        }

        if (this.shutdownRequested) {
          break;
        }

        final RemoteConsoleSession.SessionOutcome sessionOutcome = this.runSession(socketPath);
        switch (sessionOutcome.disconnectReason()) {
          case USER_EOF -> this.exitReason = ExitReason.USER_EOF;
          case UNRECOVERABLE_HANDSHAKE_FAILURE -> {
            if (!ignoreUnrecoverableHandshake) {
              this.exitReason = ExitReason.UNRECOVERABLE_HANDSHAKE_FAILURE;
            }
          }
        }
        if (sessionOutcome.didConnect()) {
          LOGGER.info(text("Disconnected from server.", NamedTextColor.RED, TextDecoration.BOLD));
          retryCount = 0;
        }
        if (sessionOutcome.disconnectReason() == RemoteConsoleSession.DisconnectReason.UNRECOVERABLE_HANDSHAKE_FAILURE
          && ignoreUnrecoverableHandshake) {
          LOGGER.warn("Retrying despite unrecoverable handshake failure because --ignore-unrecoverable-handshake is enabled.");
        }
        if (shouldQuitClient(sessionOutcome, ignoreUnrecoverableHandshake)) {
          break;
        }

        retryCount++;
        final long backoffMs = this.retryBackoffMs(retryCount);

        if (backoffMs > 0L && socketWatcher.exists()) {
          LOGGER.info("Reconnecting in {}...", formatBackoff(backoffMs));
        }
        if (!socketWatcher.waitForBackoffOrSocketDisappear(backoffMs, () -> this.shutdownRequested)) {
          break;
        }
      }
    } finally {
      this.shutdown();
      final TerminalRuntimeContext context = this.terminalContext;
      if (context != null) {
        context.close();
      }
      this.terminalContext = null;
    }

    final ExitReason exitReason = this.exitReason;
    if (exitReason == ExitReason.USER_EOF || exitReason == ExitReason.USER_INTERRUPT_WHILE_WAITING) {
      LOGGER.info("Goodbye!");
    }
    return exitReason == null ? 0 : exitReason.exitCode();
  }

  static boolean shouldQuitClient(
    final RemoteConsoleSession.SessionOutcome sessionOutcome,
    final boolean ignoreUnrecoverableHandshake
  ) {
    if (sessionOutcome.disconnectReason() == RemoteConsoleSession.DisconnectReason.UNRECOVERABLE_HANDSHAKE_FAILURE
      && ignoreUnrecoverableHandshake) {
      return false;
    }
    return sessionOutcome.quitClient();
  }

  private RemoteConsoleSession.SessionOutcome runSession(final String socketPath) {
    final TerminalRuntimeContext context = this.terminalContext;
    if (context == null) {
      throw new IllegalStateException("Terminal context is not initialized");
    }
    final RemoteConsoleSession session = new RemoteConsoleSession(
      socketPath,
      context,
      this.logExecutor,
      () -> this.shutdownRequested
    );
    this.activeSession = session;
    try {
      return session.run();
    } finally {
      this.activeSession = null;
    }
  }

  private void registerSignalHandlers() {
    final TerminalRuntimeContext context = this.terminalContext;
    if (context == null) {
      return;
    }
    context.registerInterruptHandler(() -> {
      final RemoteConsoleSession session = this.activeSession;
      if (session == null || !session.isConnected()) {
        this.exitReason = ExitReason.USER_INTERRUPT_WHILE_WAITING;
        this.shutdownRequested = true;
      }
    });
  }

  private long retryBackoffMs(final int attempt) {
    if (attempt <= 1) {
      return 0L;
    }
    final int shift = Math.min(30, attempt - 2);
    final long exponentialBackoff = INITIAL_RETRY_BACKOFF_MS << shift;
    return Math.min(MAX_RETRY_BACKOFF_MS, exponentialBackoff);
  }

  private static String formatBackoff(final long backoffMs) {
    if (backoffMs % 1000L == 0) {
      return backoffMs / 1000L + "s";
    }
    final double seconds = backoffMs / 1000.0;
    return String.format(Locale.ROOT, "%.1fs", seconds);
  }

  private void shutdown() {
    this.logExecutor.shutdown();
    try {
      if (!this.logExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        this.logExecutor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      this.logExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    final RemoteConsoleSession session = this.activeSession;
    if (session != null) {
      session.disconnect();
    }
  }

  private enum ExitReason {
    USER_EOF(0),
    USER_INTERRUPT_WHILE_WAITING(0),
    UNRECOVERABLE_HANDSHAKE_FAILURE(1);

    private final int exitCode;

    ExitReason(int exitCode) {
      this.exitCode = exitCode;
    }

    public int exitCode() {
      return this.exitCode;
    }
  }
}
