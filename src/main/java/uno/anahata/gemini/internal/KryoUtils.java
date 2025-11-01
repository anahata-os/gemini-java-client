package uno.anahata.gemini.internal;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.objenesis.strategy.StdInstantiatorStrategy;
import uno.anahata.gemini.ChatMessage;
import uno.anahata.gemini.internal.kryo.OptionalSerializer;

public class KryoUtils {

    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // Use Objenesis for classes without no-arg constructors
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        kryo.setRegistrationRequired(false); // For simplicity, but can be set to true for production
        
        // Register Google GenAI types
        kryo.register(Content.class);
        kryo.register(Part.class);
        kryo.register(Blob.class);
        kryo.register(FunctionCall.class);
        kryo.register(FunctionResponse.class);
        kryo.register(FinishReason.class);
        kryo.register(UsageMetadata.class);
        kryo.register(GroundingMetadata.class);
        kryo.register(CitationMetadata.class);
        kryo.register(Candidate.class);
        kryo.register(SafetyRating.class);
        kryo.register(HarmCategory.class);

        // Register JDK and Guava immutable types
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(ImmutableList.class);
        kryo.register(ImmutableMap.class);
        kryo.register(Optional.class, new OptionalSerializer()); // Use custom serializer for Optional

        // Register project-specific types
        kryo.register(ChatMessage.class);
        
        return kryo;
    });

    public static Kryo getKryo() {
        return kryoThreadLocal.get();
    }

    public static byte[] serialize(Object object) {
        Kryo kryo = getKryo();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Output output = new Output(byteArrayOutputStream)) {
            kryo.writeObject(output, object);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
        Kryo kryo = getKryo();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        try (Input input = new Input(byteArrayInputStream)) {
            return kryo.readObject(input, clazz);
        }
    }
}
