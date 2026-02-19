package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class HandshakeRejectReasons {
  private HandshakeRejectReasons() {
  }

  public static final String MISSING_REQUEST_ID = "missing_request_id";
  public static final String EXPECTED_HELLO = "expected_hello";
  public static final String UNSUPPORTED_TRANSPORT_EPOCH = "unsupported_transport_epoch";
  public static final String INVALID_TRANSPORT_EPOCH_RANGE = "invalid_transport_epoch_range";
  public static final String MISSING_COLOR_LEVEL = "missing_color_level";
  public static final String MISSING_CAPABILITY_NEGOTIATION_DATA = "missing_capability_negotiation_data";
  public static final String INVALID_CAPABILITY_VERSION_RANGE = "invalid_capability_version_range";
  public static final String INVALID_REQUIRED_CAPABILITY_DECLARATION = "invalid_required_capability_declaration";
  public static final String MISSING_REQUIRED_CAPABILITIES = "missing_required_capabilities";
}
