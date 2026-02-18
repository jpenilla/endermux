package xyz.jpenilla.endermux.client.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.jpenilla.endermux.protocol.FrameCodec;
import xyz.jpenilla.endermux.protocol.HandshakeRejectReasons;
import xyz.jpenilla.endermux.protocol.Message;
import xyz.jpenilla.endermux.protocol.MessageSerializer;
import xyz.jpenilla.endermux.protocol.MessageType;
import xyz.jpenilla.endermux.protocol.Payloads;
import xyz.jpenilla.endermux.protocol.ProtocolCapabilities;
import xyz.jpenilla.endermux.protocol.SocketProtocolConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketTransportIntegrationTest {
  @TempDir
  Path tempDir;

  @Test
  void handshakeRejectWithDifferentExpectedTransportEpochThrowsProtocolMismatch() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.REJECT,
        new Payloads.Reject(
          HandshakeRejectReasons.UNSUPPORTED_TRANSPORT_EPOCH,
          "Unsupported transport epoch",
          SocketProtocolConstants.TRANSPORT_EPOCH + 1,
          Set.of()
        )
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final ProtocolMismatchException ex = assertThrows(ProtocolMismatchException.class, transport::connect);
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH + 1, ex.expectedTransportEpoch());
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, ex.actualTransportEpoch());
      transport.disconnect();
    }
  }

  @Test
  void handshakeInvalidResponseTypeThrowsInvalidHandshakeResponse() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(requestId, MessageType.PONG, new Payloads.Pong()));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final InvalidHandshakeResponseException ex = assertThrows(
        InvalidHandshakeResponseException.class,
        transport::connect
      );
      assertTrue(ex.getMessage().contains("unexpected response type"));
      transport.disconnect();
    }
  }

  @Test
  void handshakeResponseRequestIdMismatchThrowsInvalidHandshakeResponse() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      assertHello(hello);
      peer.write(Message.response(
        UUID.randomUUID().toString(),
        MessageType.WELCOME,
        welcomePayload()
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final InvalidHandshakeResponseException ex = assertThrows(
        InvalidHandshakeResponseException.class,
        transport::connect
      );
      assertTrue(ex.getMessage().contains("requestId mismatch"));
      transport.disconnect();
    }
  }

  @Test
  void handshakeWelcomeUnsupportedCapabilityVersionThrowsInvalidHandshakeResponse() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.WELCOME,
        new Payloads.Welcome(
          SocketProtocolConstants.TRANSPORT_EPOCH,
          Map.of(
            ProtocolCapabilities.COMMAND_EXECUTE, ProtocolCapabilities.V1,
            ProtocolCapabilities.LOG_FORWARD, ProtocolCapabilities.V1,
            ProtocolCapabilities.INTERACTIVITY_STATUS, ProtocolCapabilities.V1,
            ProtocolCapabilities.COMPLETION, 999
          )
        )
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final InvalidHandshakeResponseException ex = assertThrows(
        InvalidHandshakeResponseException.class,
        transport::connect
      );
      assertTrue(ex.getMessage().contains("unsupported version"));
      transport.disconnect();
    }
  }

  @Test
  void handshakeWelcomeMissingRequiredCapabilityThrowsMissingRequiredCapabilitiesException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.WELCOME,
        new Payloads.Welcome(
          SocketProtocolConstants.TRANSPORT_EPOCH,
          Map.of(
            ProtocolCapabilities.COMMAND_EXECUTE, ProtocolCapabilities.V1,
            ProtocolCapabilities.LOG_FORWARD, ProtocolCapabilities.V1
          )
        )
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final MissingRequiredCapabilitiesException ex = assertThrows(MissingRequiredCapabilitiesException.class, transport::connect);
      assertTrue(ex.missingRequiredCapabilities().contains(ProtocolCapabilities.INTERACTIVITY_STATUS));
      transport.disconnect();
    }
  }

  @Test
  void handshakeRejectMissingRequiredCapabilitiesThrowsMissingRequiredCapabilitiesException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.REJECT,
        new Payloads.Reject(
          HandshakeRejectReasons.MISSING_REQUIRED_CAPABILITIES,
          "Missing required capabilities",
          null,
          Set.of(ProtocolCapabilities.INTERACTIVITY_STATUS)
        )
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final MissingRequiredCapabilitiesException ex = assertThrows(MissingRequiredCapabilitiesException.class, transport::connect);
      assertEquals("Missing required capabilities", ex.reason());
      assertTrue(ex.missingRequiredCapabilities().contains(ProtocolCapabilities.INTERACTIVITY_STATUS));
      transport.disconnect();
    }
  }

  @Test
  void handshakeRejectUnknownReasonThrowsUnknownRejectReasonException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.REJECT,
        new Payloads.Reject("future_reason", "Future reject reason", null, Set.of())
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final UnknownRejectReasonException ex = assertThrows(UnknownRejectReasonException.class, transport::connect);
      assertEquals("future_reason", ex.rejectReason());
      transport.disconnect();
    }
  }

  @Test
  void handshakeWelcomeWrongTransportEpochThrowsProtocolMismatch() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String requestId = assertHello(hello);
      peer.write(Message.response(
        requestId,
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.TRANSPORT_EPOCH + 1, selectedCapabilities())
      ));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());

      final ProtocolMismatchException ex = assertThrows(ProtocolMismatchException.class, transport::connect);
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH + 1, ex.expectedTransportEpoch());
      assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, ex.actualTransportEpoch());
      transport.disconnect();
    }
  }

  @Test
  void correlatedResponseCompletesPendingRequest() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        welcomePayload()
      ));

      final Message<?> ping = peer.readMessage();
      assertEquals(MessageType.PING, ping.type());
      assertNotNull(ping.requestId());
      peer.write(Message.response(ping.requestId(), MessageType.PONG, new Payloads.Pong()));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        final Message<Payloads.Ping> request = transport.createRequest(MessageType.PING, new Payloads.Ping());
        final Message<?> response = transport.sendMessageAndWaitForResponse(request, MessageType.PONG, 2_000L);
        assertEquals(MessageType.PONG, response.type());
        assertEquals(request.requestId(), response.requestId());
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void errorResponseSurfacesAsIOException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        welcomePayload()
      ));

      final Message<?> ping = peer.readMessage();
      peer.write(Message.response(ping.requestId(), MessageType.ERROR, new Payloads.Error("Nope", "Bad ping")));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        final Message<Payloads.Ping> request = transport.createRequest(MessageType.PING, new Payloads.Ping());
        final IOException ex = assertThrows(
          IOException.class,
          () -> transport.sendMessageAndWaitForResponse(request, MessageType.PONG, 2_000L)
        );
        assertEquals("Nope: Bad ping", ex.getMessage());
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void wrongResponseTypeSurfacesAsIOException() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        welcomePayload()
      ));

      final Message<?> ping = peer.readMessage();
      peer.write(Message.response(ping.requestId(), MessageType.PONG, new Payloads.Pong()));
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        final Message<Payloads.Ping> request = transport.createRequest(MessageType.PING, new Payloads.Ping());
        final IOException ex = assertThrows(
          IOException.class,
          () -> transport.sendMessageAndWaitForResponse(request, MessageType.COMPLETION_RESPONSE, 2_000L)
        );
        assertTrue(ex.getMessage().contains("Unexpected response type"));
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void interactivityUnavailableBlocksGatedRequestClientSide() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        welcomePayload()
      ));
      Thread.sleep(100L);
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        assertFalse(transport.isInteractivityAvailable());
        final Message<Payloads.CompletionRequest> request = transport.createRequest(
          MessageType.COMPLETION_REQUEST,
          new Payloads.CompletionRequest("help", 4)
        );
        final IOException ex = assertThrows(
          IOException.class,
          () -> transport.sendMessageAndWaitForResponse(request, MessageType.COMPLETION_RESPONSE, 2_000L)
        );
        assertEquals("Interactivity is currently unavailable", ex.getMessage());
      } finally {
        transport.disconnect();
      }
    }
  }

  @Test
  void interactivityStatusMessageUpdatesTransportState() throws Exception {
    try (ScriptedServer server = this.startServer(peer -> {
      final Message<?> hello = peer.readMessage();
      final String helloRequestId = assertHello(hello);
      peer.write(Message.response(
        helloRequestId,
        MessageType.WELCOME,
        welcomePayload()
      ));
      peer.write(Message.unsolicited(MessageType.INTERACTIVITY_STATUS, new Payloads.InteractivityStatus(true)));
      Thread.sleep(100L);
    })) {
      final SocketTransport transport = new SocketTransport(server.socketPath().toString());
      transport.connect();
      try {
        waitForCondition(Duration.ofSeconds(2), transport::isInteractivityAvailable);
        assertTrue(transport.isInteractivityAvailable());
      } finally {
        transport.disconnect();
      }
    }
  }

  private ScriptedServer startServer(final ServerScript script) throws Exception {
    final Path socket = this.tempDir.resolve("sock-" + UUID.randomUUID() + ".sock");
    final ScriptedServer server = new ScriptedServer(socket, script);
    final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (!Files.exists(socket)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out waiting for socket file");
      }
      Thread.sleep(10L);
    }
    return server;
  }

  private static String assertHello(final Message<?> hello) {
    assertNotNull(hello);
    assertEquals(MessageType.HELLO, hello.type());
    assertInstanceOf(Payloads.Hello.class, hello.payload());
    final Payloads.Hello payload = (Payloads.Hello) hello.payload();
    assertEquals(SocketProtocolConstants.TRANSPORT_EPOCH, payload.transportEpoch());
    assertNotNull(payload.colorLevel());
    assertNotNull(payload.capabilities());
    assertNotNull(payload.requiredCapabilities());
    assertTrue(payload.requiredCapabilities().contains(ProtocolCapabilities.INTERACTIVITY_STATUS));
    assertNotNull(hello.requestId());
    return hello.requestId();
  }

  private static Payloads.Welcome welcomePayload() {
    return new Payloads.Welcome(SocketProtocolConstants.TRANSPORT_EPOCH, selectedCapabilities());
  }

  private static Map<String, Integer> selectedCapabilities() {
    return Map.of(
      ProtocolCapabilities.COMMAND_EXECUTE, ProtocolCapabilities.V1,
      ProtocolCapabilities.LOG_FORWARD, ProtocolCapabilities.V1,
      ProtocolCapabilities.INTERACTIVITY_STATUS, ProtocolCapabilities.V1,
      ProtocolCapabilities.COMPLETION, ProtocolCapabilities.V1,
      ProtocolCapabilities.SYNTAX_HIGHLIGHT, ProtocolCapabilities.V1,
      ProtocolCapabilities.PARSE, ProtocolCapabilities.V1
    );
  }

  private static void waitForCondition(final Duration timeout, final Condition condition) throws Exception {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.test()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Condition was not met before timeout");
      }
      Thread.sleep(10L);
    }
  }

  @FunctionalInterface
  private interface Condition {
    boolean test();
  }

  @FunctionalInterface
  private interface ServerScript {
    void run(TestPeer peer) throws Exception;
  }

  private static final class ScriptedServer implements AutoCloseable {
    private final ServerSocketChannel serverChannel;
    private final Path socketPath;
    private final Thread thread;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    ScriptedServer(final Path socketPath, final ServerScript script) throws IOException {
      this.socketPath = socketPath;
      this.serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      this.serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
      this.thread = Thread.ofVirtual().start(() -> {
        try (SocketChannel client = this.serverChannel.accept(); TestPeer peer = new TestPeer(client)) {
          script.run(peer);
        } catch (final Throwable t) {
          this.failure.set(t);
        }
      });
    }

    Path socketPath() {
      return this.socketPath;
    }

    @Override
    public void close() throws IOException {
      try {
        this.serverChannel.close();
      } finally {
        try {
          this.thread.join(2_000L);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while waiting for test server thread", e);
        }
      }
      final Throwable t = this.failure.get();
      if (t != null) {
        if (t instanceof IOException ex) {
          throw ex;
        }
        if (t instanceof RuntimeException ex) {
          throw ex;
        }
        throw new IOException("Test server failed", t);
      }
    }
  }

  private static final class TestPeer implements AutoCloseable {
    private final DataInputStream input;
    private final DataOutputStream output;
    private final MessageSerializer serializer = MessageSerializer.createStandard();

    TestPeer(final SocketChannel channel) throws IOException {
      this.input = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
      this.output = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
    }

    Message<?> readMessage() throws IOException {
      final byte[] frame = FrameCodec.readFrame(this.input);
      if (frame == null) {
        return null;
      }
      return this.serializer.deserialize(new String(frame, StandardCharsets.UTF_8));
    }

    void write(final Message<?> message) throws IOException {
      final byte[] frame = this.serializer.serialize(message).getBytes(StandardCharsets.UTF_8);
      FrameCodec.writeFrame(this.output, frame);
    }

    @Override
    public void close() throws IOException {
      this.output.close();
      this.input.close();
    }
  }
}
