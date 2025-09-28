package uno.anahata.gemini.internal;

import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;

public class PartAdapter implements JsonSerializer<Part>, JsonDeserializer<Part> {

    @Override
    public JsonElement serialize(Part src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        if (src.text().isPresent()) {
            jsonObject.addProperty("text", src.text().get());
        } else if (src.functionCall().isPresent()) {
            jsonObject.add("functionCall", context.serialize(src.functionCall().get()));
        } else if (src.functionResponse().isPresent()) {
            jsonObject.add("functionResponse", context.serialize(src.functionResponse().get()));
        } else if (src.inlineData().isPresent()) {
            jsonObject.add("inlineData", context.serialize(src.inlineData().get()));
        }
        return jsonObject;
    }

    @Override
    public Part deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        if (jsonObject.has("text")) {
            return Part.fromText(jsonObject.get("text").getAsString());
        } else if (jsonObject.has("functionCall")) {
            JsonObject fcJson = jsonObject.getAsJsonObject("functionCall");
            String name = fcJson.get("name").getAsString();
            Map<String, Object> args = context.deserialize(fcJson.get("args"), new TypeToken<Map<String, Object>>() {}.getType());
            return Part.fromFunctionCall(name, args);
        } else if (jsonObject.has("functionResponse")) {
            JsonObject frJson = jsonObject.getAsJsonObject("functionResponse");
            String name = frJson.get("name").getAsString();
            Map<String, Object> response = context.deserialize(frJson.get("response"), new TypeToken<Map<String, Object>>() {}.getType());
            return Part.fromFunctionResponse(name, response);
        } else if (jsonObject.has("inlineData")) {
            Blob blob = context.deserialize(jsonObject.get("inlineData"), Blob.class);
            return Part.fromBytes(blob.data().get(), blob.mimeType().get());
        }
        throw new JsonParseException("Unknown Part type: " + json.toString());
    }
}
