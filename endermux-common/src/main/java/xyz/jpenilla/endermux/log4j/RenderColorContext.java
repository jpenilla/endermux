package xyz.jpenilla.endermux.log4j;

import net.kyori.ansi.ColorLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class RenderColorContext {
  private static final ColorLevel LOCAL = ColorLevel.compute();
  private static final ThreadLocal<@Nullable ColorLevel> CURRENT = new ThreadLocal<>();

  private RenderColorContext() {
  }

  public static Scope push(final ColorLevel colorLevel) {
    final ColorLevel previous = CURRENT.get();
    CURRENT.set(colorLevel);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  public static ColorLevel current() {
    final ColorLevel current = CURRENT.get();
    return current != null ? current : LOCAL;
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
