package xyz.jpenilla.endermux.protocol;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ProtocolCapabilities {
  private ProtocolCapabilities() {
  }

  public static final String COMMAND_EXECUTE = "command_execute";
  public static final String LOG_FORWARD = "log_forward";
  public static final String INTERACTIVITY_STATUS = "interactivity_status";
  public static final String COMPLETION = "completion";
  public static final String SYNTAX_HIGHLIGHT = "syntax_highlight";
  public static final String PARSE = "parse";

  public static final int V1 = 1;

  // Client policy may include compatibility ranges for older server releases.
  private static final Map<String, CapabilityVersionRange> CLIENT_SUPPORTED_CAPABILITIES = Map.of(
    COMMAND_EXECUTE, new CapabilityVersionRange(V1, V1),
    LOG_FORWARD, new CapabilityVersionRange(V1, V1),
    INTERACTIVITY_STATUS, new CapabilityVersionRange(V1, V1),
    COMPLETION, new CapabilityVersionRange(V1, V1),
    SYNTAX_HIGHLIGHT, new CapabilityVersionRange(V1, V1),
    PARSE, new CapabilityVersionRange(V1, V1)
  );

  private static final Set<String> CLIENT_REQUIRED_CAPABILITIES = Set.of(
    COMMAND_EXECUTE,
    LOG_FORWARD,
    INTERACTIVITY_STATUS
  );

  // Server policy represents the versions currently implemented by this server release.
  private static final Map<String, CapabilityVersionRange> SERVER_SUPPORTED_CAPABILITIES = Map.of(
    COMMAND_EXECUTE, new CapabilityVersionRange(V1, V1),
    LOG_FORWARD, new CapabilityVersionRange(V1, V1),
    INTERACTIVITY_STATUS, new CapabilityVersionRange(V1, V1),
    COMPLETION, new CapabilityVersionRange(V1, V1),
    SYNTAX_HIGHLIGHT, new CapabilityVersionRange(V1, V1),
    PARSE, new CapabilityVersionRange(V1, V1)
  );

  public static Map<String, CapabilityVersionRange> clientSupportedCapabilities() {
    return CLIENT_SUPPORTED_CAPABILITIES;
  }

  public static Set<String> clientRequiredCapabilities() {
    return CLIENT_REQUIRED_CAPABILITIES;
  }

  public static Map<String, CapabilityVersionRange> serverSupportedCapabilities() {
    return SERVER_SUPPORTED_CAPABILITIES;
  }

  public static @Nullable Integer negotiateHighestCommonVersion(
    final CapabilityVersionRange clientRange,
    final CapabilityVersionRange serverRange
  ) {
    return clientRange.highestCommonVersion(serverRange);
  }
}
