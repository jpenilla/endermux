package xyz.jpenilla.endermux.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.ansi.ColorLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageSerializerTest {

  private final MessageSerializer serializer = MessageSerializer.createStandard();

  @Test
  void roundTripAllMessageTypes() {
    for (final Message<?> original : sampleMessages()) {
      final String json = this.serializer.serialize(original);
      final Message<?> decoded = this.serializer.deserialize(json);

      assertNotNull(decoded, () -> "Decoded message was null for JSON: " + json);
      assertEquals(original.type(), decoded.type());
      assertEquals(original.requestId(), decoded.requestId());
      assertEquals(original.payload(), decoded.payload());
    }
  }

  @Test
  void serializeOmitsRequestIdWhenNull() {
    final Message<Payloads.Ping> message = Message.unsolicited(MessageType.PING, new Payloads.Ping());
    final String json = this.serializer.serialize(message);
    final JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertFalse(root.has("requestId"));
  }

  @Test
  void deserializeInvalidMessagesReturnsNull() {
    assertNull(this.serializer.deserialize("{\"data\":{}}"));
    assertNull(this.serializer.deserialize("{\"type\":\"NOT_A_REAL_TYPE\",\"data\":{}}"));
    assertNull(this.serializer.deserialize("{\"type\":\"PING\",\"data\":"));
    assertNull(this.serializer.deserialize("[1,2,3]"));
  }

  private static List<Message<?>> sampleMessages() {
    return List.of(
      Message.response(
        "req-hello",
        MessageType.HELLO,
        new Payloads.Hello(
          SocketProtocolConstants.CLIENT_SUPPORTED_TRANSPORT_EPOCH_RANGE,
          ColorLevel.INDEXED_16,
          ProtocolCapabilities.clientSupportedCapabilities(),
          ProtocolCapabilities.clientRequiredCapabilities()
        )
      ),
      Message.response("req-complete", MessageType.COMPLETION_REQUEST, new Payloads.CompletionRequest("say he", 6)),
      Message.response("req-highlight", MessageType.SYNTAX_HIGHLIGHT_REQUEST, new Payloads.SyntaxHighlightRequest("say hi")),
      Message.response("req-parse", MessageType.PARSE_REQUEST, new Payloads.ParseRequest("say hi", 4)),
      Message.unsolicited(MessageType.COMMAND_EXECUTE, new Payloads.CommandExecute("say hi")),
      Message.response("req-ping", MessageType.PING, new Payloads.Ping()),
      Message.unsolicited(MessageType.LOG_SUBSCRIBE, new Payloads.LogSubscribe()),
      Message.response(
        "req-welcome",
        MessageType.WELCOME,
        new Payloads.Welcome(SocketProtocolConstants.TRANSPORT_EPOCH, Map.of(ProtocolCapabilities.COMMAND_EXECUTE, 1))
      ),
      Message.response(
        "req-reject",
        MessageType.REJECT,
        new Payloads.Reject(
          HandshakeRejectReasons.MISSING_REQUIRED_CAPABILITIES,
          "Missing required capabilities",
          SocketProtocolConstants.TRANSPORT_EPOCH,
          Set.of(ProtocolCapabilities.INTERACTIVITY_STATUS)
        )
      ),
      Message.response("req-completion-response", MessageType.COMPLETION_RESPONSE, new Payloads.CompletionResponse(
        List.of(
          new Payloads.CompletionResponse.CandidateInfo("help", "help", "show commands"),
          new Payloads.CompletionResponse.CandidateInfo("stop", "stop", null)
        )
      )),
      Message.response("req-highlight-response", MessageType.SYNTAX_HIGHLIGHT_RESPONSE, new Payloads.SyntaxHighlightResponse("say hi", "<green>say</green> hi")),
      Message.response("req-parse-response", MessageType.PARSE_RESPONSE, new Payloads.ParseResponse(
        "hi",
        2,
        1,
        List.of("say", "hi"),
        "say hi",
        6
      )),
      Message.unsolicited(MessageType.LOG_FORWARD, new Payloads.LogForward("server started")),
      Message.response("req-pong", MessageType.PONG, new Payloads.Pong()),
      Message.response("req-error", MessageType.ERROR, new Payloads.Error("Bad request", null)),
      Message.unsolicited(MessageType.INTERACTIVITY_STATUS, new Payloads.InteractivityStatus(true))
    );
  }
}
