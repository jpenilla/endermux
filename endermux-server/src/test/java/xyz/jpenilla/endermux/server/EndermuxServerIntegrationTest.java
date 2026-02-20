package xyz.jpenilla.endermux.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.kyori.ansi.ColorLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.jpenilla.endermux.ansi.ColorLevelContext;
import xyz.jpenilla.endermux.protocol.CapabilityVersionRange;
import xyz.jpenilla.endermux.protocol.FrameCompressionType;
import xyz.jpenilla.endermux.protocol.FrameCodec;
import xyz.jpenilla.endermux.protocol.HandshakeRejectReasons;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.ProtocolCapabilities;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;
import xyz.jpenilla.endermux.protocol.TimedRead;
import xyz.jpenilla.endermux.server.api.InteractiveConsoleHooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndermuxServerIntegrationTest {
  @TempDir
  Path tempDir;

  private EndermuxServer server;

  @AfterEach
  void tearDown() {
    if (this.server != null) {
      this.server.stop();
    }
  }

  @Test
  void handshakeSuccessSendsWelcomeAndInitialInteractivityStatus() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(
        requestId,
        MessageType.HELLO,
        hello(ColorLevel.INDEXED_16)
      ));

      final Message<?> welcome = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(welcome);
      assertEquals(MessageType.WELCOME, welcome.type());
      assertEquals(requestId, welcome.requestId());
      final Payloads.Welcome welcomePayload = (Payloads.Welcome) welcome.payload();
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, welcomePayload.transportEpoch());
      assertEquals(ProtocolCapabilities.V1, welcomePayload.selectedCapabilities().get(ProtocolCapabilities.COMMAND_EXECUTE));

      final Message<?> status = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(status);
      assertEquals(MessageType.INTERACTIVITY_STATUS, status.type());
      assertNull(status.requestId());
      assertFalse(((Payloads.InteractivityStatus) status.payload()).available());
    }
  }

  @Test
  void handshakeRejectsMissingRequestId() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      client.send(Message.unsolicited(
        MessageType.HELLO,
        hello(ColorLevel.INDEXED_16)
      ));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertNull(reject.requestId());

      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals(HandshakeRejectReasons.MISSING_REQUEST_ID, payload.reason());
      assertEquals("Missing requestId", payload.message());
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, payload.expectedTransportEpoch());
    }
  }

  @Test
  void handshakeRejectsNonHelloFirstMessage() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(requestId, MessageType.PING, new Payloads.Ping()));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertEquals(requestId, reject.requestId());
      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals(HandshakeRejectReasons.EXPECTED_HELLO, payload.reason());
      assertEquals("Expected HELLO", payload.message());
    }
  }

  @Test
  void handshakeRejectsUnsupportedTransportEpoch() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(
        requestId,
        MessageType.HELLO,
        helloWithTransportEpochRange(
          new CapabilityVersionRange(
            SocketProtocolConstants.TRANSPORT_EPOCH + 1,
            SocketProtocolConstants.TRANSPORT_EPOCH + 1
          ),
          ColorLevel.INDEXED_16
        )
      ));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertEquals(requestId, reject.requestId());

      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals(HandshakeRejectReasons.UNSUPPORTED_TRANSPORT_EPOCH, payload.reason());
      assertEquals("Unsupported transport epoch", payload.message());
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, payload.expectedTransportEpoch());
    }
  }

  @Test
  void handshakeRejectsInvalidTransportEpochRange() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      client.send(Message.response(
        requestId,
        MessageType.HELLO,
        helloWithTransportEpochRange(
          new CapabilityVersionRange(
            SocketProtocolConstants.TRANSPORT_EPOCH,
            SocketProtocolConstants.TRANSPORT_EPOCH,
            Set.of(SocketProtocolConstants.TRANSPORT_EPOCH + 1)
          ),
          ColorLevel.INDEXED_16
        )
      ));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertEquals(requestId, reject.requestId());

      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals(HandshakeRejectReasons.INVALID_TRANSPORT_EPOCH_RANGE, payload.reason());
      assertEquals("Invalid transport epoch range", payload.message());
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, payload.expectedTransportEpoch());
    }
  }

  @Test
  void handshakeRejectsMissingRequiredCapability() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String requestId = UUID.randomUUID().toString();
      final Map<String, CapabilityVersionRange> supportedCapabilities = new HashMap<>(
        ProtocolCapabilities.clientSupportedCapabilities()
      );
      supportedCapabilities.remove(ProtocolCapabilities.INTERACTIVITY_STATUS);
      client.send(Message.response(
        requestId,
        MessageType.HELLO,
        new Payloads.Hello(
          new CapabilityVersionRange(
            SocketProtocolConstants.TRANSPORT_EPOCH,
            SocketProtocolConstants.TRANSPORT_EPOCH
          ),
          ColorLevel.INDEXED_16,
          supportedCapabilities,
          Set.of(ProtocolCapabilities.INTERACTIVITY_STATUS)
        )
      ));

      final Message<?> reject = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(reject);
      assertEquals(MessageType.REJECT, reject.type());
      assertEquals(requestId, reject.requestId());

      final Payloads.Reject payload = (Payloads.Reject) reject.payload();
      assertEquals(HandshakeRejectReasons.MISSING_REQUIRED_CAPABILITIES, payload.reason());
      assertEquals("Missing required capabilities", payload.message());
      assertTrue(payload.missingRequiredCapabilities().contains(ProtocolCapabilities.INTERACTIVITY_STATUS));
    }
  }

  @Test
  void interactivityAndLogSubscribeFlow() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String helloRequestId = UUID.randomUUID().toString();
      client.send(Message.response(
        helloRequestId,
        MessageType.HELLO,
        hello(ColorLevel.INDEXED_16)
      ));
      assertEquals(MessageType.WELCOME, client.readMessageWithTimeout(Duration.ofSeconds(2)).type());
      final Message<?> initialStatus = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(initialStatus);
      assertFalse(((Payloads.InteractivityStatus) initialStatus.payload()).available());

      final String completionRequestId = UUID.randomUUID().toString();
      client.send(Message.response(
        completionRequestId,
        MessageType.COMPLETION_REQUEST,
        new Payloads.CompletionRequest("help", 4)
      ));
      final Message<?> unavailableError = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(unavailableError);
      assertEquals(MessageType.ERROR, unavailableError.type());
      assertEquals(completionRequestId, unavailableError.requestId());
      assertEquals("Interactivity is currently unavailable", ((Payloads.Error) unavailableError.payload()).message());

      this.server.enableInteractivity(InteractiveConsoleHooks.builder().build());
      final Message<?> updatedStatus = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(updatedStatus);
      assertEquals(MessageType.INTERACTIVITY_STATUS, updatedStatus.type());
      assertTrue(((Payloads.InteractivityStatus) updatedStatus.payload()).available());

      final String completionRequestId2 = UUID.randomUUID().toString();
      client.send(Message.response(
        completionRequestId2,
        MessageType.COMPLETION_REQUEST,
        new Payloads.CompletionRequest("help", 4)
      ));
      final Message<?> unsupportedError = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(unsupportedError);
      assertEquals(MessageType.ERROR, unsupportedError.type());
      assertEquals(completionRequestId2, unsupportedError.requestId());
      assertEquals("Completions are not supported", ((Payloads.Error) unsupportedError.payload()).message());

      client.send(Message.unsolicited(MessageType.LOG_SUBSCRIBE, new Payloads.LogSubscribe()));
      final String pingRequestId = UUID.randomUUID().toString();
      client.send(Message.response(pingRequestId, MessageType.PING, new Payloads.Ping()));
      final Message<?> pong = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(pong);
      assertEquals(MessageType.PONG, pong.type());
      assertEquals(pingRequestId, pong.requestId());

      this.server.broadcastLog(level -> "hello from server");

      final Message<?> forwarded = client.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(forwarded);
      assertEquals(MessageType.LOG_FORWARD, forwarded.type());
      final Payloads.LogForward payload = (Payloads.LogForward) forwarded.payload();
      assertEquals("hello from server", payload.rendered());
    }
  }

  @Test
  void logForwardLargePayloadUsesGzipCompression() throws Exception {
    final Path socket = this.startServer();

    try (TestClient client = TestClient.connect(socket)) {
      final String helloRequestId = UUID.randomUUID().toString();
      client.send(Message.response(
        helloRequestId,
        MessageType.HELLO,
        hello(ColorLevel.INDEXED_16)
      ));
      assertEquals(MessageType.WELCOME, client.readMessageWithTimeout(Duration.ofSeconds(2)).type());
      assertEquals(MessageType.INTERACTIVITY_STATUS, client.readMessageWithTimeout(Duration.ofSeconds(2)).type());

      client.send(Message.unsolicited(MessageType.LOG_SUBSCRIBE, new Payloads.LogSubscribe()));

      final String logLine = "x".repeat(2048);
      this.server.broadcastLog(level -> logLine);

      final TestClient.RawFrame rawFrame = client.readRawFrameWithTimeout(Duration.ofSeconds(2));
      assertNotNull(rawFrame);
      assertEquals(FrameCompressionType.GZIP.wireValue(), rawFrame.compressionType());
      assertNotNull(rawFrame.message());
      assertEquals(MessageType.LOG_FORWARD, rawFrame.message().type());
      final Payloads.LogForward payload = (Payloads.LogForward) rawFrame.message().payload();
      assertEquals(logLine, payload.rendered());
    }
  }

  @Test
  void completionRequestUsesSessionColorContext() throws Exception {
    final Path socket = this.startServer();
    this.server.enableInteractivity(InteractiveConsoleHooks.builder()
      .completer((command, cursor) -> new Payloads.CompletionResponse(List.of(
        new Payloads.CompletionResponse.CandidateInfo(
          "value",
          "display",
          ColorLevelContext.current().name()
        )
      )))
      .build());

    try (TestClient noneClient = TestClient.connect(socket); TestClient trueColorClient = TestClient.connect(socket)) {
      final String noneHelloRequestId = UUID.randomUUID().toString();
      noneClient.send(Message.response(
        noneHelloRequestId,
        MessageType.HELLO,
        hello(ColorLevel.NONE)
      ));
      final Message<?> noneWelcome = noneClient.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(noneWelcome);
      assertEquals(MessageType.WELCOME, noneWelcome.type());
      assertEquals(noneHelloRequestId, noneWelcome.requestId());
      final Message<?> noneStatus = noneClient.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(noneStatus);
      assertEquals(MessageType.INTERACTIVITY_STATUS, noneStatus.type());
      assertTrue(((Payloads.InteractivityStatus) noneStatus.payload()).available());

      final String trueColorHelloRequestId = UUID.randomUUID().toString();
      trueColorClient.send(Message.response(
        trueColorHelloRequestId,
        MessageType.HELLO,
        hello(ColorLevel.TRUE_COLOR)
      ));
      final Message<?> trueColorWelcome = trueColorClient.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(trueColorWelcome);
      assertEquals(MessageType.WELCOME, trueColorWelcome.type());
      assertEquals(trueColorHelloRequestId, trueColorWelcome.requestId());
      final Message<?> trueColorStatus = trueColorClient.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(trueColorStatus);
      assertEquals(MessageType.INTERACTIVITY_STATUS, trueColorStatus.type());
      assertTrue(((Payloads.InteractivityStatus) trueColorStatus.payload()).available());

      final String noneCompletionRequestId = UUID.randomUUID().toString();
      noneClient.send(Message.response(
        noneCompletionRequestId,
        MessageType.COMPLETION_REQUEST,
        new Payloads.CompletionRequest("help", 4)
      ));
      final Message<?> noneCompletionResponse = noneClient.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(noneCompletionResponse);
      assertEquals(MessageType.COMPLETION_RESPONSE, noneCompletionResponse.type());
      assertEquals(noneCompletionRequestId, noneCompletionResponse.requestId());
      final Payloads.CompletionResponse nonePayload = (Payloads.CompletionResponse) noneCompletionResponse.payload();
      assertEquals(ColorLevel.NONE.name(), nonePayload.candidates().get(0).description());

      final String trueColorCompletionRequestId = UUID.randomUUID().toString();
      trueColorClient.send(Message.response(
        trueColorCompletionRequestId,
        MessageType.COMPLETION_REQUEST,
        new Payloads.CompletionRequest("help", 4)
      ));
      final Message<?> trueColorCompletionResponse = trueColorClient.readMessageWithTimeout(Duration.ofSeconds(2));
      assertNotNull(trueColorCompletionResponse);
      assertEquals(MessageType.COMPLETION_RESPONSE, trueColorCompletionResponse.type());
      assertEquals(trueColorCompletionRequestId, trueColorCompletionResponse.requestId());
      final Payloads.CompletionResponse trueColorPayload = (Payloads.CompletionResponse) trueColorCompletionResponse.payload();
      assertEquals(ColorLevel.TRUE_COLOR.name(), trueColorPayload.candidates().get(0).description());
    }
  }

  private Path startServer() throws Exception {
    final Path socket = this.tempDir.resolve("endermux.sock");
    this.server = new EndermuxServer(
      socket,
      4
    );
    this.server.start();

    final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (!Files.exists(socket)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out waiting for server socket to be created");
      }
      Thread.sleep(10L);
    }

    return socket;
  }

  private static Payloads.Hello hello(final ColorLevel colorLevel) {
    return helloWithTransportEpochRange(
      new CapabilityVersionRange(
        SocketProtocolConstants.TRANSPORT_EPOCH,
        SocketProtocolConstants.TRANSPORT_EPOCH
      ),
      colorLevel
    );
  }

  private static Payloads.Hello helloWithTransportEpochRange(
    final CapabilityVersionRange transportEpochRange,
    final ColorLevel colorLevel
  ) {
    return new Payloads.Hello(
      transportEpochRange,
      colorLevel,
      ProtocolCapabilities.clientSupportedCapabilities(),
      ProtocolCapabilities.clientRequiredCapabilities()
    );
  }

  private static final class TestClient implements AutoCloseable {
    private final SocketChannel channel;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final MessageSerializer serializer = MessageSerializer.createStandard();

    private TestClient(final SocketChannel channel) throws IOException {
      this.channel = channel;
      this.input = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
      this.output = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
    }

    static TestClient connect(final Path socket) throws IOException {
      final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
      channel.connect(UnixDomainSocketAddress.of(socket));
      return new TestClient(channel);
    }

    void send(final Message<?> message) throws IOException {
      final byte[] data = this.serializer.serialize(message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      FrameCodec.writeFrame(this.output, data);
    }

    Message<?> readMessageWithTimeout(final Duration timeout) throws IOException {
      final RawFrame frame = this.readRawFrameWithTimeout(timeout);
      return frame == null ? null : frame.message();
    }

    RawFrame readRawFrameWithTimeout(final Duration timeout) throws IOException {
      return TimedRead.read(
        () -> {
          final int length;
          try {
            length = this.input.readInt();
          } catch (final EOFException e) {
            return null;
          }
          final int compressionType = this.input.readUnsignedByte();
          final byte[] payload = new byte[length - 1];
          this.input.readFully(payload);
          final byte[] messageBytes = switch (FrameCompressionType.fromWireValue(compressionType)) {
            case NONE -> payload;
            case GZIP -> ungzip(payload);
          };
          return new RawFrame(
            compressionType,
            this.serializer.deserialize(new String(messageBytes, java.nio.charset.StandardCharsets.UTF_8))
          );
        },
        timeout.toMillis(),
        "Timed out waiting for test client read",
        () -> {
          try {
            this.close();
          } catch (final IOException ignored) {
          }
        },
        200L
      );
    }

    private static byte[] ungzip(final byte[] payload) throws IOException {
      try (ByteArrayInputStream input = new ByteArrayInputStream(payload); GZIPInputStream gzip = new GZIPInputStream(input)) {
        return gzip.readAllBytes();
      }
    }

    record RawFrame(int compressionType, Message<?> message) {
    }

    @Override
    public void close() throws IOException {
      this.output.close();
      this.input.close();
      this.channel.close();
    }
  }
}
