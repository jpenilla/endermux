package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SocketProtocolConstants {

  private SocketProtocolConstants() {
  }

  /**
   * Transport epoch version for framing + envelope + handshake structure.
   */
  public static final int TRANSPORT_EPOCH = 17;

  /**
   * Lowest transport epoch this client release can negotiate.
   */
  public static final int MIN_SUPPORTED_TRANSPORT_EPOCH = 17;

  public static final CapabilityVersionRange CLIENT_SUPPORTED_TRANSPORT_EPOCH_RANGE =
    new CapabilityVersionRange(MIN_SUPPORTED_TRANSPORT_EPOCH, TRANSPORT_EPOCH);

  /**
   * Maximum compressed payload bytes in a frame (not including 1-byte compression type).
   */
  public static final int MAX_COMPRESSED_PAYLOAD_SIZE_BYTES = 1024 * 1024;

  /**
   * Maximum uncompressed payload bytes after decompression.
   */
  public static final int MAX_UNCOMPRESSED_PAYLOAD_SIZE_BYTES = 4 * 1024 * 1024;

  public static final long HANDSHAKE_TIMEOUT_MS = 2000L;

  public static final long HANDSHAKE_TIMEOUT_JOIN_MS = 1000L;

  public static final long SYNTAX_HIGHLIGHT_TIMEOUT_MS = 1000L;

  public static final long COMPLETION_TIMEOUT_MS = 5000L;
}
