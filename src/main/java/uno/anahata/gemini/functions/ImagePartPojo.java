package uno.anahata.gemini.functions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A simple POJO to hold the data of an image part for serialization within a FunctionResponse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagePartPojo {
    private String mimeType;
    private byte[] data;
}
