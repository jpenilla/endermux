package xyz.jpenilla.endermux.log4j;

import java.util.List;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.endermux.ansi.ColorLevelContext;

@Plugin(name = "EndermuxRichMessage", category = PatternConverter.CATEGORY)
@ConverterKeys({"EndermuxRichMessage"})
@PerformanceSensitive("allocation")
public final class RichMessageConverter extends LogEventPatternConverter {
  private final List<PatternFormatter> fallbackFormatters;

  private RichMessageConverter(final List<PatternFormatter> fallbackFormatters) {
    super("EndermuxRichMessage", null);
    this.fallbackFormatters = fallbackFormatters;
  }

  @Override
  public void format(final LogEvent event, final StringBuilder toAppendTo) {
    final @Nullable String richMessage = RichLogContext.renderedFor(
      event.getContextData(),
      ColorLevelContext.current()
    );
    if (richMessage != null) {
      toAppendTo.append(richMessage);
      return;
    }

    for (int i = 0, size = this.fallbackFormatters.size(); i < size; i++) {
      this.fallbackFormatters.get(i).format(event, toAppendTo);
    }
  }

  @Override
  public boolean handlesThrowable() {
    for (final PatternFormatter formatter : this.fallbackFormatters) {
      if (formatter.handlesThrowable()) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable RichMessageConverter newInstance(final Configuration config, final String[] options) {
    if (options.length != 1) {
      LOGGER.error("Incorrect number of options on EndermuxRichMessage. Expected 1 received " + options.length);
      return null;
    }
    if (options[0] == null) {
      LOGGER.error("No fallback pattern supplied on EndermuxRichMessage");
      return null;
    }

    final PatternParser parser = PatternLayout.createPatternParser(config);
    final List<PatternFormatter> fallbackFormatters = parser.parse(options[0]);
    return new RichMessageConverter(fallbackFormatters);
  }
}
