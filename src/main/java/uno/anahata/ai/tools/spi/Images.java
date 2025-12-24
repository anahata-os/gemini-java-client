/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi;

import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import uno.anahata.ai.Chat;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;

/**
 * A Core Function provider that groups all functions for generating images.
 */
public class Images {

    @AIToolMethod("Generates a single image based on a text prompt and saves it to a specified file path.")
    public static String create(
        @AIToolParam("The text prompt to describe the desired image.") String prompt,
        @AIToolParam("The absolute file path where the generated PNG image should be saved.") String filePath
    ) throws Exception {
        
        if (prompt == null || prompt.trim().isEmpty()) {
            return "Error: The 'prompt' parameter was not set.";
        }
        if (filePath == null || filePath.trim().isEmpty()) {
            return "Error: The 'filePath' parameter was not set.";
        }

        try {
            Client client = Chat.getCallingInstance().getGoogleGenAIClient();
            
            GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities("IMAGE")
                .build();

            String modelName = "gemini-2.5-flash-image"; 

            GenerateContentResponse response = client.models.generateContent(modelName, prompt, config);

            for (Candidate candidate : response.candidates().get()) {
                for (Part part : candidate.content().get().parts().get()) { 
                    if (part.inlineData().isPresent()) {
                        Blob blob = part.inlineData().get();
                        byte[] imageBytes = blob.data().get();
                        
                        Path outputPath = Paths.get(filePath);
                        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                            fos.write(imageBytes);
                        }
                        
                        return "SUCCESS: Successfully generated and saved image to " + filePath + ". Size: " + imageBytes.length + " bytes.";
                    }
                }
            }
            return "Error: API call succeeded, but no image data found in the response.";

        } catch (Exception e) {
            return "FAILURE: An error occurred during image generation: " + e.toString();
        }
    }
}