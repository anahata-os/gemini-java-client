package uno.anahata.gemini.internal;

import com.google.genai.types.Blob;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class PartAdapter implements JsonSerializer<Part>, JsonDeserializer<Part> {

    @Override
    public JsonElement serialize(Part src, Type typeOfSrc, JsonSerializationContext context) {
        if (src.text().isPresent()) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("text", src.text().get());
            return jsonObject;
        } else if (src.functionCall().isPresent()) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("functionCall", context.serialize(src.functionCall().get()));
            return jsonObject;
        } else if (src.functionResponse().isPresent()) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("functionResponse", context.serialize(src.functionResponse().get()));
            return jsonObject;
        } else if (src.inlineData().isPresent()) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("inlineData", context.serialize(src.inlineData().get()));
            return jsonObject;
        }
        return new JsonObject(); // Should not happen
    }

    @Override
    public Part deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        if (jsonObject.has("text")) {
            return Part.fromText(jsonObject.get("text").getAsString());
        } else if (jsonObject.has("functionCall")) {
            FunctionCall functionCall = context.deserialize(jsonObject.get("functionCall"), FunctionCall.class);
            return Part.fromFunctionCall(functionCall.name().get(), functionCall.args().get());
        } else if (jsonObject.has("functionResponse")) {
            FunctionResponse functionResponse = context.deserialize(jsonObject.get("functionResponse"), FunctionResponse.class);
            return Part.fromFunctionResponse(functionResponse.name().get(), functionResponse.response().get());
        } else if (jsonObject.has("inlineData")) {
            Blob blob = context.deserialize(jsonObject.get("inlineData"), Blob.class);
            return Part.fromBytes(blob.data().get(), blob.mimeType().get());
        }
        throw new JsonParseException("Unknown Part type: " + json.toString());
    }
}