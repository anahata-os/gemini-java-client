package uno.anahata.gemini.internal;

import com.google.genai.types.Content;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ContentAdapter implements JsonSerializer<Content>, JsonDeserializer<Content> {

    @Override
    public JsonElement serialize(Content src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        src.role().ifPresent(role -> jsonObject.addProperty("role", role));
        if (src.parts().isPresent()) {
            jsonObject.add("parts", context.serialize(src.parts().get()));
        }
        return jsonObject;
    }

    @Override
    public Content deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String role = Optional.ofNullable(jsonObject.get("role")).map(JsonElement::getAsString).orElse(null);
        List<Part> parts = new ArrayList<>();
        if (jsonObject.has("parts")) {
            parts = context.deserialize(jsonObject.get("parts"), new TypeToken<List<Part>>() {}.getType());
        }
        return Content.builder().role(role).parts(parts).build();
    }
}