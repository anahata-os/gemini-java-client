package uno.anahata.gemini.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public final class GsonUtils {

    private static final Gson GSON = createGson();

    public static Gson getGson() {
        return GSON;
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
                .create();
    }

    private static class OptionalTypeAdapterFactory implements com.google.gson.TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            if (typeToken.getRawType() != Optional.class) {
                return null;
            }
            final Type type = typeToken.getType();
            final Type valueType = ((ParameterizedType) type).getActualTypeArguments()[0];
            final TypeAdapter<?> valueAdapter = gson.getAdapter(TypeToken.get(valueType));

            return (TypeAdapter<T>) new OptionalTypeAdapter(valueAdapter);
        }
    }

    private static class OptionalTypeAdapter<E> extends TypeAdapter<Optional<E>> {
        private final TypeAdapter<E> valueAdapter;

        public OptionalTypeAdapter(TypeAdapter<E> valueAdapter) {
            this.valueAdapter = valueAdapter;
        }

        @Override
        public void write(JsonWriter out, Optional<E> value) throws IOException {
            if (value != null && value.isPresent()) {
                valueAdapter.write(out, value.get());
            } else {
                out.nullValue();
            }
        }

        @Override
        public Optional<E> read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            }
            E value = valueAdapter.read(in);
            return Optional.ofNullable(value);
        }
    }
}
