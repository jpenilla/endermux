package xyz.jpenilla.endermux.client.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jspecify.annotations.NullMarked;

import static net.kyori.adventure.text.Component.text;

@NullMarked
final class SocketPathWatcher {
  private static final long BACKOFF_POLL_INTERVAL_MS = 200L;

  private final Path resolvedSocketPath;
  private final String displayPath;
  private final long pollIntervalMs;
  private final ComponentLogger logger;

  SocketPathWatcher(
    final String socketPath,
    final long pollIntervalMs,
    final ComponentLogger logger
  ) {
    this.resolvedSocketPath = Paths.get(socketPath).toAbsolutePath().normalize();
    this.displayPath = socketPath;
    this.pollIntervalMs = pollIntervalMs;
    this.logger = logger;
  }

  boolean exists() {
    return Files.exists(this.resolvedSocketPath);
  }

  boolean waitForSocket(final BooleanSupplier shutdownRequested) {
    if (this.exists()) {
      return true;
    }

    final Path parentDir = this.parentDirOrThrow();
    this.logger.info(text()
      .content("Waiting for socket to exist: " + this.displayPath)
      .decorate(TextDecoration.ITALIC)
      .build());

    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      parentDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
      if (this.exists()) {
        return true;
      }
      while (true) {
        if (shutdownRequested.getAsBoolean()) {
          return false;
        }
        final WatchKey key;
        try {
          key = watchService.poll(this.pollIntervalMs, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
          if (shutdownRequested.getAsBoolean()) {
            return false;
          }
          this.logger.debug("Interrupted while waiting for the socket to appear", e);
          continue;
        }
        if (key == null) {
          continue;
        }
        for (final WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            final Path created = parentDir.resolve((Path) event.context());
            if (created.equals(this.resolvedSocketPath)) {
              return true;
            }
          }
        }
        if (!key.reset()) {
          this.logger.warn("File watching failed, falling back to polling");
          break;
        }
      }
    } catch (final IOException e) {
      this.logger.warn("File watching unavailable ({}), falling back to polling", e.getMessage());
    }

    return this.pollUntilSocketExists(shutdownRequested);
  }

  boolean waitForBackoffOrSocketDisappear(
    final long backoffMs,
    final BooleanSupplier shutdownRequested
  ) {
    if (backoffMs <= 0L || !this.exists()) {
      return true;
    }

    final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(backoffMs);
    final Path parentDir = this.parentDirOrThrow();
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      parentDir.register(watchService, StandardWatchEventKinds.ENTRY_DELETE);
      while (true) {
        if (shutdownRequested.getAsBoolean()) {
          return false;
        }
        if (!this.exists()) {
          return true;
        }
        final long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
          return true;
        }
        final WatchKey key;
        try {
          final long pollMs = Math.max(1L, Math.min(BACKOFF_POLL_INTERVAL_MS, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
          key = watchService.poll(pollMs, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
          if (shutdownRequested.getAsBoolean()) {
            return false;
          }
          this.logger.debug("Interrupted while waiting for socket disappearance", e);
          continue;
        }
        if (key == null) {
          continue;
        }
        for (final WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
            final Path deleted = parentDir.resolve((Path) event.context());
            if (deleted.equals(this.resolvedSocketPath)) {
              return true;
            }
          }
        }
        if (!key.reset()) {
          this.logger.warn("File watching failed during reconnect backoff, falling back to polling");
          break;
        }
      }
    } catch (final IOException e) {
      this.logger.warn("File watching unavailable during reconnect backoff ({}), falling back to polling", e.getMessage());
    }

    return this.pollBackoffUntil(deadlineNanos, shutdownRequested);
  }

  private Path parentDirOrThrow() {
    final Path parentDir = this.resolvedSocketPath.getParent();
    if (parentDir == null || !Files.isDirectory(parentDir)) {
      throw new IllegalArgumentException("Parent directory does not exist: " +
        (parentDir != null ? parentDir : this.resolvedSocketPath));
    }
    return parentDir;
  }

  private boolean pollUntilSocketExists(final BooleanSupplier shutdownRequested) {
    while (!this.exists()) {
      if (shutdownRequested.getAsBoolean()) {
        return false;
      }
      try {
        Thread.sleep(this.pollIntervalMs);
      } catch (final InterruptedException e) {
        if (shutdownRequested.getAsBoolean()) {
          return false;
        }
        this.logger.debug("Interrupted while polling for socket availability", e);
      }
    }
    return true;
  }

  private boolean pollBackoffUntil(
    final long deadlineNanos,
    final BooleanSupplier shutdownRequested
  ) {
    while (true) {
      if (shutdownRequested.getAsBoolean()) {
        return false;
      }
      if (!this.exists()) {
        return true;
      }
      final long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0L) {
        return true;
      }
      final long sleepMs = Math.max(1L, Math.min(BACKOFF_POLL_INTERVAL_MS, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
      try {
        Thread.sleep(sleepMs);
      } catch (final InterruptedException e) {
        if (shutdownRequested.getAsBoolean()) {
          return false;
        }
        this.logger.debug("Interrupted while sleeping for reconnect backoff", e);
      }
    }
  }
}
