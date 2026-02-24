package xyz.jpenilla.endermux.client.runtime;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.io.IoBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class StreamRedirection {
  private static final AtomicBoolean REDIRECTED = new AtomicBoolean();
  private static final AtomicReference<@Nullable PrintStream> ORIGINAL_OUT = new AtomicReference<>();
  private static final AtomicReference<@Nullable PrintStream> ORIGINAL_ERR = new AtomicReference<>();

  private StreamRedirection() {
  }

  public static void replaceStreams() {
    if (!REDIRECTED.compareAndSet(false, true)) {
      originalOut().println("Attempted to redirect streams when they were already redirected");
      return;
    }

    ORIGINAL_OUT.compareAndSet(null, System.out);
    ORIGINAL_ERR.compareAndSet(null, System.err);

    final PrintStream stdout = IoBuilder.forLogger("STDOUT")
      .setLevel(Level.INFO)
      .buildPrintStream();
    final PrintStream stderr = IoBuilder.forLogger("STDERR")
      .setLevel(Level.ERROR)
      .buildPrintStream();
    System.setOut(stdout);
    System.setErr(stderr);
  }

  public static void restoreOriginalStreams() {
    if (!REDIRECTED.compareAndSet(true, false)) {
      originalOut().println("Attempted to restore streams when they were not redirected");
      return;
    }

    final PrintStream out = ORIGINAL_OUT.getAndSet(null);
    final PrintStream err = ORIGINAL_ERR.getAndSet(null);
    System.setOut(out);
    System.setErr(err);
  }

  public static PrintStream originalOut() {
    final PrintStream original = ORIGINAL_OUT.get();
    return original != null ? original : System.out;
  }

  public static PrintStream originalErr() {
    final PrintStream original = ORIGINAL_ERR.get();
    return original != null ? original : System.err;
  }
}
