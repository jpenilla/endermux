package xyz.jpenilla.endermux.protocol;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.ansi.ColorLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Payloads {

  private Payloads() {
  }

  // Client -> Server payloads

  public record Hello(
    CapabilityVersionRange transportEpochRange,
    ColorLevel colorLevel,
    Map<String, CapabilityVersionRange> capabilities,
    Set<String> requiredCapabilities
  ) implements MessagePayload {
  }

  public record CompletionRequest(String command, int cursor) implements MessagePayload {
  }

  public record SyntaxHighlightRequest(String command) implements MessagePayload {
  }

  public record ParseRequest(String command, int cursor) implements MessagePayload {
  }

  public record CommandExecute(String command) implements MessagePayload {
  }

  public record Ping() implements MessagePayload {
  }

  public record LogSubscribe() implements MessagePayload {
  }

  // Server -> Client payloads

  public record Welcome(
    int transportEpoch,
    Map<String, Integer> selectedCapabilities
  ) implements MessagePayload {
  }

  public record Reject(
    String reason,
    String message,
    @Nullable Integer expectedTransportEpoch,
    Set<String> missingRequiredCapabilities
  ) implements MessagePayload {
  }

  public record CompletionResponse(List<CandidateInfo> candidates) implements MessagePayload {
    public record CandidateInfo(String value, String display, @Nullable String description) {
    }
  }

  public record SyntaxHighlightResponse(String command, String highlighted) implements MessagePayload {
  }

  public record ParseResponse(
    String word,
    int wordCursor,
    int wordIndex,
    List<String> words,
    String line,
    int cursor
  ) implements MessagePayload {
  }

  public record LogForward(
    String rendered
  ) implements MessagePayload {
  }

  public record Pong() implements MessagePayload {
  }

  public record Error(String message, @Nullable String details) implements MessagePayload {
  }

  public record InteractivityStatus(boolean available) implements MessagePayload {
  }
}
