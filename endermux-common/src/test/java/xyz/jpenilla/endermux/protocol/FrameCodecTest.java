package xyz.jpenilla.endermux.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameCodecTest {

  @Test
  void readWriteRoundTrip() throws Exception {
    final byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final DataOutputStream out = new DataOutputStream(bytes);

    FrameCodec.writeFrame(out, payload);

    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    final byte[] decoded = FrameCodec.readFrame(in);

    assertArrayEquals(payload, decoded);
  }

  @Test
  void eofWhileReadingLengthReturnsNull() throws Exception {
    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
    assertNull(FrameCodec.readFrame(in));
  }

  @Test
  void invalidFrameLengthThrowsProtocolException() {
    final ProtocolException zero = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(inputForLength(0, 0)));
    assertEquals("Invalid frame size: 0", zero.getMessage());

    final ProtocolException negative = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(inputForLength(-1, 0)));
    assertEquals("Invalid frame size: -1", negative.getMessage());

    final int tooLarge = SocketProtocolConstants.MAX_COMPRESSED_PAYLOAD_SIZE_BYTES + 2;
    final ProtocolException large = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(inputForLength(tooLarge, 0)));
    assertEquals("Invalid frame size: " + tooLarge, large.getMessage());
  }

  @Test
  void oversizedUncompressedPayloadWriteThrowsProtocolException() {
    final byte[] oversize = new byte[SocketProtocolConstants.MAX_UNCOMPRESSED_PAYLOAD_SIZE_BYTES + 1];
    final DataOutputStream out = new DataOutputStream(new ByteArrayOutputStream());

    final ProtocolException ex = assertThrows(ProtocolException.class, () -> FrameCodec.writeFrame(out, oversize));
    assertEquals("Uncompressed payload too large: " + oversize.length, ex.getMessage());
  }

  @Test
  void oversizedCompressedPayloadWriteThrowsProtocolException() {
    final byte[] oversize = new byte[SocketProtocolConstants.MAX_COMPRESSED_PAYLOAD_SIZE_BYTES + 1];
    final DataOutputStream out = new DataOutputStream(new ByteArrayOutputStream());

    final ProtocolException ex = assertThrows(
      ProtocolException.class,
      () -> FrameCodec.writeFrame(out, oversize, FrameCompressionType.NONE)
    );
    assertEquals("Compressed payload too large: " + oversize.length, ex.getMessage());
  }

  @Test
  void unsupportedCompressionThrowsProtocolException() {
    final ProtocolException ex = assertThrows(
      ProtocolException.class,
      () -> FrameCodec.readFrame(inputForLength(2, 2))
    );
    assertEquals("Unsupported frame compression: 2", ex.getMessage());
  }

  @Test
  void gzipRoundTrip() throws Exception {
    final byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final DataOutputStream out = new DataOutputStream(bytes);

    FrameCodec.writeFrame(out, payload, FrameCompressionType.GZIP);

    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    final byte[] decoded = FrameCodec.readFrame(in);

    assertArrayEquals(payload, decoded);
  }

  @Test
  void gzipTooLargeAfterDecompressionThrowsProtocolException() throws Exception {
    final byte[] oversized = new byte[SocketProtocolConstants.MAX_UNCOMPRESSED_PAYLOAD_SIZE_BYTES + 1];
    final ByteArrayOutputStream compressedPayloadBytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressedPayloadBytes)) {
      gzip.write(oversized);
    }

    final byte[] compressedPayload = compressedPayloadBytes.toByteArray();
    final ByteArrayOutputStream frameBytes = new ByteArrayOutputStream();
    try (DataOutputStream frameOut = new DataOutputStream(frameBytes)) {
      frameOut.writeInt(compressedPayload.length + 1);
      frameOut.writeByte(FrameCompressionType.GZIP.wireValue());
      frameOut.write(compressedPayload);
    }

    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(frameBytes.toByteArray()));
    final ProtocolException ex = assertThrows(ProtocolException.class, () -> FrameCodec.readFrame(in));
    assertEquals("Decompressed frame too large: " + oversized.length, ex.getMessage());
  }

  private static DataInputStream inputForLength(final int length, final int compression) throws Exception {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(length);
      if (length > 0) {
        out.writeByte(compression);
      }
      if (length > 1) {
        out.write(new byte[length - 1]);
      }
    }
    return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
  }
}
