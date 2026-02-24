package xyz.jpenilla.endermux.client;

import java.util.concurrent.Callable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import xyz.jpenilla.endermux.client.runtime.EndermuxClient;
import xyz.jpenilla.endermux.client.runtime.StreamRedirection;

import static net.kyori.adventure.text.Component.text;

@Command(
  name = "endermux-client",
  mixinStandardHelpOptions = true,
  versionProvider = EndermuxCli.VersionProvider.class,
  description = "Endermux Client - Fully-featured remote console experience for Minecraft servers implementing the Endermux protocol."
)
@NullMarked
public final class EndermuxCli implements Callable<Integer> {
  private static final Logger LOGGER = LoggerFactory.getLogger(EndermuxCli.class);

  public static final Component VERSION_MESSAGE = text()
    .append(text("Endermux", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
    .append(text(" Client ").decorate(TextDecoration.BOLD))
    .append(text("v" + EndermuxCli.class.getPackage().getImplementationVersion()))
    .build();

  @Option(
    names = {"--socket", "-s"},
    defaultValue = "console.sock",
    description = "Path to the console socket."
  )
  private String socketPath = "console.sock";

  @Option(
    names = "--debug",
    defaultValue = "false",
    description = "Enable debug logging."
  )
  private boolean debug;

  @Option(
    names = "--ignore-unrecoverable-handshake",
    defaultValue = "false",
    description = "Keep retrying even when handshake fails with an unrecoverable protocol error."
  )
  private boolean ignoreUnrecoverableHandshake;

  static void main(final String[] args) {
    final int exitCode = new CommandLine(new EndermuxCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    try {
      StreamRedirection.replaceStreams();

      if (this.debug) {
        this.enableDebugLogging();
      }

      final EndermuxClient client = new EndermuxClient();
      return client.run(this.socketPath, this.ignoreUnrecoverableHandshake);
    } catch (final Exception e) {
      LOGGER.error("Error starting Endermux client", e);
      return 1;
    } finally {
      LogManager.shutdown();
      StreamRedirection.restoreOriginalStreams();
    }
  }

  private void enableDebugLogging() {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    final LoggerConfig root = context.getConfiguration().getRootLogger();
    root.setLevel(Level.DEBUG);
    context.updateLoggers();
  }

  static class VersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
      return new String[]{
        ANSIComponentSerializer.ansi().serialize(VERSION_MESSAGE)
      };
    }
  }
}
