package xyz.jpenilla.endermux.client.runtime;

import java.io.PrintStream;
import org.jline.reader.LineReader;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TerminalOutput {
  private static final Object LOCK = new Object();
  private static volatile @Nullable LineReader LINE_READER = null;
  private static volatile @Nullable Terminal TERMINAL = null;

  private TerminalOutput() {
  }

  public static void setTerminal(final @Nullable Terminal terminal) {
    synchronized (LOCK) {
      TERMINAL = terminal;
    }
  }

  public static void setLineReader(final @Nullable LineReader lineReader) {
    synchronized (LOCK) {
      LINE_READER = lineReader;
    }
  }

  public static void write(final String message) {
    synchronized (LOCK) {
      final LineReader lineReader = LINE_READER;
      if (lineReader != null) {
        lineReader.printAbove(message);
        return;
      }

      final Terminal terminal = TERMINAL;
      if (terminal != null) {
        terminal.writer().print(message);
        terminal.writer().flush();
      } else {
        final PrintStream originalOut = StreamRedirection.originalOut();
        originalOut.print(message);
        originalOut.flush();
      }
    }
  }

  public static void redisplay() {
    synchronized (LOCK) {
      final LineReader lineReader = LINE_READER;
      if (lineReader != null) {
        // bypass callWidget to avoid isReading race
        ((LineReaderImpl) lineReader).redisplay();
      }
    }
  }
}
