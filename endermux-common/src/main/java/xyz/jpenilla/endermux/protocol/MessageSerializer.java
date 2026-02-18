package xyz.jpenilla.endermux.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class MessageSerializer {

  private static final Gson GSON = new GsonBuilder()
    .serializeNulls()
    .create();

  public static MessageSerializer createStandard() {
    return new MessageSerializer();
  }

  public String serialize(final Message<?> message) {
    final JsonObject root = new JsonObject();
    root.addProperty("type", message.type().id());

    if (message.requestId() != null) {
      root.addProperty("requestId", message.requestId());
    }

    root.add("data", GSON.toJsonTree(message.payload()));

    return root.toString();
  }

  public @Nullable Message<?> deserialize(final String json) {
    try {
      final JsonObject root = JsonParser.parseString(json).getAsJsonObject();

      if (!root.has("type")) {
        return null;
      }

      final String typeName = root.get("type").getAsString();
      final @Nullable MessageType type = MessageType.findById(typeName);
      if (type == null) {
        return null;
      }

      final String requestId = root.has("requestId") && !root.get("requestId").isJsonNull()
        ? root.get("requestId").getAsString()
        : null;

      final Class<? extends MessagePayload> payloadClass = type.payloadType();

      final JsonObject data = root.has("data") && root.get("data").isJsonObject()
        ? root.getAsJsonObject("data")
        : new JsonObject();
      final MessagePayload payload = GSON.fromJson(data, payloadClass);

      return new Message<>(type, requestId, payload);

    } catch (final JsonSyntaxException | IllegalStateException | ClassCastException e) {
      return null;
    }
  }
}
