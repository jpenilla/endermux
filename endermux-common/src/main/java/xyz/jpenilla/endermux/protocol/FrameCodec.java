package xyz.jpenilla.endermux.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class FrameCodec {

  private FrameCodec() {
  }

  public static byte @Nullable [] readFrame(final DataInputStream in) throws IOException {
    final int length;
    try {
      length = in.readInt();
    } catch (final EOFException e) {
      return null;
    }

    if (length <= 0 || length > SocketProtocolConstants.MAX_COMPRESSED_PAYLOAD_SIZE_BYTES + 1) {
      throw new ProtocolException("Invalid frame size: " + length);
    }

    final FrameCompressionType compression = FrameCompressionType.fromWireValue(in.readUnsignedByte());
    final byte[] data = new byte[length - 1];
    in.readFully(data);
    return switch (compression) {
      case NONE -> validateUncompressedPayloadSize(data);
      case GZIP -> gunzip(data);
    };
  }

  public static void writeFrame(final DataOutputStream out, final byte[] data) throws IOException {
    writeFrame(out, data, FrameCompressionType.NONE);
  }

  public static void writeFrame(
    final DataOutputStream out,
    final byte[] data,
    final FrameCompressionType compression
  ) throws IOException {
    final FrameCompressionType compressionType = Objects.requireNonNull(compression, "compression");
    if (data.length > SocketProtocolConstants.MAX_UNCOMPRESSED_PAYLOAD_SIZE_BYTES) {
      throw new ProtocolException("Uncompressed payload too large: " + data.length);
    }

    final byte[] payload = switch (compressionType) {
      case NONE -> data;
      case GZIP -> gzip(data);
    };

    if (payload.length > SocketProtocolConstants.MAX_COMPRESSED_PAYLOAD_SIZE_BYTES) {
      throw new ProtocolException("Compressed payload too large: " + payload.length);
    }

    out.writeInt(payload.length + 1);
    out.writeByte(compressionType.wireValue());
    out.write(payload);
    out.flush();
  }

  private static byte[] gunzip(final byte[] data) throws IOException {
    try (
      ByteArrayInputStream input = new ByteArrayInputStream(data);
      GZIPInputStream gzip = new GZIPInputStream(input);
      ByteArrayOutputStream out = new ByteArrayOutputStream()
    ) {
      final byte[] buffer = new byte[8192];
      int total = 0;
      int read;
      while ((read = gzip.read(buffer)) != -1) {
        total += read;
        if (total > SocketProtocolConstants.MAX_UNCOMPRESSED_PAYLOAD_SIZE_BYTES) {
          throw new ProtocolException("Decompressed frame too large: " + total);
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    }
  }

  private static byte[] validateUncompressedPayloadSize(final byte[] data) throws ProtocolException {
    if (data.length > SocketProtocolConstants.MAX_UNCOMPRESSED_PAYLOAD_SIZE_BYTES) {
      throw new ProtocolException("Uncompressed payload too large: " + data.length);
    }
    return data;
  }

  private static byte[] gzip(final byte[] data) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
        gzip.write(data);
      }
      return out.toByteArray();
    }
  }
}
