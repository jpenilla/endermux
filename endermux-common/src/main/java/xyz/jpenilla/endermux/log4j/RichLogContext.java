package xyz.jpenilla.endermux.log4j;

import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.ansi.ColorLevel;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.ansi.ColorLevelContext;

@NullMarked
public final class RichLogContext {
  private static final String KEY_PREFIX = "endermux.richMessage.";
  private static final String KEY_NONE = KEY_PREFIX + "none";
  private static final String KEY_INDEXED_8 = KEY_PREFIX + "indexed8";
  private static final String KEY_INDEXED_16 = KEY_PREFIX + "indexed16";
  private static final String KEY_INDEXED_256 = KEY_PREFIX + "indexed256";
  private static final String KEY_TRUE_COLOR = KEY_PREFIX + "trueColor";

  private RichLogContext() {
  }

  public static Scope pushRenderedByColorLevel(final Function<ANSIComponentSerializer, String> renderedByColorLevel) {
    return pushRenderedByColorLevel(
      renderedByColorLevel.apply(ColorLevelContext.ANSI_NONE),
      renderedByColorLevel.apply(ColorLevelContext.ANSI_INDEXED_8),
      renderedByColorLevel.apply(ColorLevelContext.ANSI_INDEXED_16),
      renderedByColorLevel.apply(ColorLevelContext.ANSI_INDEXED_256),
      renderedByColorLevel.apply(ColorLevelContext.ANSI_TRUE_COLOR)
    );
  }

  public static Scope pushComponent(final Component component) {
    return pushRenderedByColorLevel(
      ColorLevelContext.ANSI_NONE.serialize(component),
      ColorLevelContext.ANSI_INDEXED_8.serialize(component),
      ColorLevelContext.ANSI_INDEXED_16.serialize(component),
      ColorLevelContext.ANSI_INDEXED_256.serialize(component),
      ColorLevelContext.ANSI_TRUE_COLOR.serialize(component)
    );
  }

  public static Scope pushRenderedByColorLevel(
    final String none,
    final String indexed8,
    final String indexed16,
    final String indexed256,
    final String trueColor
  ) {
    final String previousNone = ThreadContext.get(KEY_NONE);
    final String previousIndexed8 = ThreadContext.get(KEY_INDEXED_8);
    final String previousIndexed16 = ThreadContext.get(KEY_INDEXED_16);
    final String previousIndexed256 = ThreadContext.get(KEY_INDEXED_256);
    final String previousTrueColor = ThreadContext.get(KEY_TRUE_COLOR);

    ThreadContext.put(KEY_NONE, none);
    ThreadContext.put(KEY_INDEXED_8, indexed8);
    ThreadContext.put(KEY_INDEXED_16, indexed16);
    ThreadContext.put(KEY_INDEXED_256, indexed256);
    ThreadContext.put(KEY_TRUE_COLOR, trueColor);

    return () -> {
      restore(KEY_NONE, previousNone);
      restore(KEY_INDEXED_8, previousIndexed8);
      restore(KEY_INDEXED_16, previousIndexed16);
      restore(KEY_INDEXED_256, previousIndexed256);
      restore(KEY_TRUE_COLOR, previousTrueColor);
    };
  }

  public static @Nullable String renderedFor(final ReadOnlyStringMap contextData, final ColorLevel colorLevel) {
    return contextData.getValue(keyFor(colorLevel));
  }

  private static String keyFor(final ColorLevel colorLevel) {
    return switch (colorLevel) {
      case NONE -> KEY_NONE;
      case INDEXED_8 -> KEY_INDEXED_8;
      case INDEXED_16 -> KEY_INDEXED_16;
      case INDEXED_256 -> KEY_INDEXED_256;
      case TRUE_COLOR -> KEY_TRUE_COLOR;
    };
  }

  private static void restore(final String key, final @Nullable String value) {
    if (value == null) {
      ThreadContext.remove(key);
    } else {
      ThreadContext.put(key, value);
    }
  }

  private static ANSIComponentSerializer serializer(final ColorLevel colorLevel) {
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
