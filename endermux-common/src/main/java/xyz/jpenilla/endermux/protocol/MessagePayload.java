package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface MessagePayload permits
  Payloads.Hello,
  Payloads.CompletionRequest,
  Payloads.SyntaxHighlightRequest,
  Payloads.ParseRequest,
  Payloads.CommandExecute,
  Payloads.Ping,
  Payloads.LogSubscribe,
  Payloads.Welcome,
  Payloads.Reject,
  Payloads.CompletionResponse,
  Payloads.SyntaxHighlightResponse,
  Payloads.ParseResponse,
  Payloads.LogForward,
  Payloads.Pong,
  Payloads.Error,
  Payloads.InteractivityStatus {
}
