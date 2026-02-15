package xyz.jpenilla.endermux.server.log4j;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.ansi.ColorLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.endermux.log4j.RenderColorContext;
import xyz.jpenilla.endermux.server.EndermuxServer;

@NullMarked
public final class RemoteLogForwarder implements EndermuxForwardingAppender.LogForwardingTarget {
  private static final Logger LOGGER = LogManager.getLogger();

  private static final long ERROR_LOG_INTERVAL_MS = 60_000;

  private final EndermuxServer endermuxServer;
  private final Layout<? extends Serializable> renderedLogLayout;
  private final AtomicLong failureCount = new AtomicLong(0);
  private volatile long lastErrorLogTime = 0;

  public RemoteLogForwarder(final EndermuxServer endermuxServer, final Layout<? extends Serializable> renderedLogLayout) {
    this.endermuxServer = endermuxServer;
    this.renderedLogLayout = renderedLogLayout;
  }

  @Override
  public void forward(final LogEvent event) {
    final EndermuxServer manager = this.endermuxServer;
    if (!manager.isRunning()) {
      return;
    }

    try {
      final LogEvent immutable = event.toImmutable();
      manager.broadcastLog(colorLevel -> this.render(immutable, colorLevel));
    } catch (final Exception e) {
      this.handleForwardingError(e);
    }
  }

  private String render(final LogEvent event, final ColorLevel colorLevel) {
    try (final RenderColorContext.Scope _ = RenderColorContext.push(colorLevel)) {
      return this.renderedLogLayout.toSerializable(event).toString();
    }
  }

  private void handleForwardingError(final Exception e) {
    final long count = this.failureCount.incrementAndGet();
    final long now = System.currentTimeMillis();

    if (now - this.lastErrorLogTime > ERROR_LOG_INTERVAL_MS) {
      this.lastErrorLogTime = now;
      LOGGER.debug(
        "Failed to forward log event to socket clients (total failures: {}): {}",
        count,
        e.getMessage(),
        e
      );
    }
  }

}
