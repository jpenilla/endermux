package xyz.jpenilla.endermux.ansi;

import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.ansi.ColorLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ColorLevelContext {
  public static final ColorLevel LOCAL_COLOR_LEVEL = ColorLevel.compute();
  public static final ANSIComponentSerializer ANSI_LOCAL = makeSerializer(LOCAL_COLOR_LEVEL);
  public static final ANSIComponentSerializer ANSI_NONE = makeSerializer(ColorLevel.NONE);
  public static final ANSIComponentSerializer ANSI_INDEXED_8 = makeSerializer(ColorLevel.INDEXED_8);
  public static final ANSIComponentSerializer ANSI_INDEXED_16 = makeSerializer(ColorLevel.INDEXED_16);
  public static final ANSIComponentSerializer ANSI_INDEXED_256 = makeSerializer(ColorLevel.INDEXED_256);
  public static final ANSIComponentSerializer ANSI_TRUE_COLOR = makeSerializer(ColorLevel.TRUE_COLOR);
  private static final ThreadLocal<@Nullable ColorLevel> CURRENT = new ThreadLocal<>();

  private ColorLevelContext() {
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
    return current != null ? current : LOCAL_COLOR_LEVEL;
  }

  public static ANSIComponentSerializer currentSerializer() {
    final ColorLevel current = CURRENT.get();
    if (current == null) {
      return ANSI_LOCAL;
    }
    return switch (current) {
      case NONE -> ANSI_NONE;
      case INDEXED_8 -> ANSI_INDEXED_8;
      case INDEXED_16 -> ANSI_INDEXED_16;
      case INDEXED_256 -> ANSI_INDEXED_256;
      case TRUE_COLOR -> ANSI_TRUE_COLOR;
    };
  }

  private static ANSIComponentSerializer makeSerializer(final ColorLevel colorLevel) {
    return ANSIComponentSerializer.builder()
      .colorLevel(colorLevel)
      .build();
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
