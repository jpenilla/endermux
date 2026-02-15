package xyz.jpenilla.endermux.server.log4j;

import java.io.Serializable;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jspecify.annotations.Nullable;

@Plugin(
  name = "EndermuxForwardingAppender",
  category = Core.CATEGORY_NAME,
  elementType = Appender.ELEMENT_TYPE
)
public final class EndermuxForwardingAppender extends AbstractAppender {

  public static @Nullable LogForwardingTarget TARGET = null;
  public static @Nullable EndermuxForwardingAppender INSTANCE = null;

  public EndermuxForwardingAppender(
    final String name,
    final Filter filter,
    final Layout<? extends Serializable> layout
  ) {
    super(name, filter, layout != null ? layout : PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
    INSTANCE = this;
  }

  @PluginFactory
  public static EndermuxForwardingAppender createAppender(
    @PluginAttribute("name") String name,
    @PluginElement("Filter") Filter filter,
    @PluginElement("Layout") Layout<? extends Serializable> layout,
    @PluginConfiguration Configuration configuration
  ) {
    if (layout == null) {
      layout = PatternLayout.createDefaultLayout(configuration);
    }
    return new EndermuxForwardingAppender(name, filter, layout);
  }

  @Override
  public void append(final LogEvent event) {
    final LogForwardingTarget target = TARGET;
    if (target != null) {
      target.forward(event);
    }
  }

  public interface LogForwardingTarget {
    void forward(LogEvent event);
  }
}
