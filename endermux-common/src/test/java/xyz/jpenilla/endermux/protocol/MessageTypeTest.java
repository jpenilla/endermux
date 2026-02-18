package xyz.jpenilla.endermux.protocol;

import net.kyori.ansi.ColorLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTypeTest {

  @Test
  void serverTypeForServerPayloads() {
    assertEquals(MessageType.WELCOME, MessageType.serverTypeForPayload(new Payloads.Welcome(8, java.util.Map.of())));
    assertEquals(MessageType.REJECT, MessageType.serverTypeForPayload(new Payloads.Reject("bad", "Bad", 8, java.util.Set.of())));
    assertEquals(MessageType.COMPLETION_RESPONSE, MessageType.serverTypeForPayload(new Payloads.CompletionResponse(java.util.List.of())));
    assertEquals(MessageType.SYNTAX_HIGHLIGHT_RESPONSE, MessageType.serverTypeForPayload(new Payloads.SyntaxHighlightResponse("cmd", "hl")));
    assertEquals(MessageType.PARSE_RESPONSE, MessageType.serverTypeForPayload(new Payloads.ParseResponse("w", 0, 0, java.util.List.of(), "line", 0)));
    assertEquals(MessageType.LOG_FORWARD, MessageType.serverTypeForPayload(new Payloads.LogForward("msg")));
    assertEquals(MessageType.PONG, MessageType.serverTypeForPayload(new Payloads.Pong()));
    assertEquals(MessageType.ERROR, MessageType.serverTypeForPayload(new Payloads.Error("oops", null)));
    assertEquals(MessageType.INTERACTIVITY_STATUS, MessageType.serverTypeForPayload(new Payloads.InteractivityStatus(true)));
  }

  @Test
  void serverTypeForClientPayloadsThrows() {
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.Hello(
      8,
      ColorLevel.INDEXED_16,
      java.util.Map.of(),
      java.util.Set.of()
    )));
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.CompletionRequest("cmd", 0)));
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.SyntaxHighlightRequest("cmd")));
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.ParseRequest("cmd", 0)));
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.CommandExecute("cmd")));
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.Ping()));
    assertThrows(IllegalArgumentException.class, () -> MessageType.serverTypeForPayload(new Payloads.LogSubscribe()));
  }

  @Test
  void findByPayloadClassUsesMessageTypeMetadata() {
    assertEquals(MessageType.HELLO, MessageType.findByPayloadClass(Payloads.Hello.class));
    assertEquals(MessageType.COMMAND_EXECUTE, MessageType.findByPayloadClass(Payloads.CommandExecute.class));
    assertEquals(MessageType.INTERACTIVITY_STATUS, MessageType.findByPayloadClass(Payloads.InteractivityStatus.class));
  }

  @Test
  void capabilityAndInteractivityMetadataMatchesRequestTypes() {
    assertEquals(ProtocolCapabilities.COMPLETION, MessageType.COMPLETION_REQUEST.capability());
    assertEquals(ProtocolCapabilities.SYNTAX_HIGHLIGHT, MessageType.SYNTAX_HIGHLIGHT_REQUEST.capability());
    assertEquals(ProtocolCapabilities.PARSE, MessageType.PARSE_REQUEST.capability());
    assertEquals(ProtocolCapabilities.COMMAND_EXECUTE, MessageType.COMMAND_EXECUTE.capability());
    assertTrue(MessageType.COMPLETION_REQUEST.requiresInteractivity());
    assertTrue(MessageType.COMMAND_EXECUTE.requiresInteractivity());
    assertNull(MessageType.PING.capability());
  }

  @Test
  void idLookupAndRegistryAccess() {
    assertEquals("HELLO", MessageType.HELLO.id());
    assertEquals(MessageType.HELLO, MessageType.findById("HELLO"));
    assertEquals(MessageType.HELLO, MessageType.byIdOrThrow("HELLO"));
    assertNull(MessageType.findById("NOT_A_TYPE"));
    assertThrows(IllegalArgumentException.class, () -> MessageType.byIdOrThrow("NOT_A_TYPE"));
    assertTrue(MessageType.allTypes().contains(MessageType.HELLO));
  }
}
