package uno.anahata.gemini.internal.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.util.Optional;

/**
 * A custom Kryo serializer for {@link java.util.Optional}.
 * <p>
 * Standard Java serialization does not support Optional, and Kryo's default
 * JavaSerializer relies on it. This serializer handles Optional by writing a
 * boolean to indicate if a value is present, followed by the value itself if it
 * exists.
 *
 * @author Anahata
 */
public class OptionalSerializer extends Serializer<Optional<?>> {

    public OptionalSerializer() {
        setImmutable(true);
    }

    @Override
    public void write(Kryo kryo, Output output, Optional<?> object) {
        if (object.isPresent()) {
            output.writeBoolean(true);
            kryo.writeClassAndObject(output, object.get());
        } else {
            output.writeBoolean(false);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Optional<?> read(Kryo kryo, Input input, Class<? extends Optional<?>> type) {
        if (input.readBoolean()) {
            return Optional.ofNullable(kryo.readClassAndObject(input));
        } else {
            return Optional.empty();
        }
    }
}
