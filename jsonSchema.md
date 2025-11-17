# JSON Schema Generation Knowledge Dump

This document summarizes the findings from our deep-dive investigation into generating `FunctionDeclaration` schemas from Java classes.

## 1. The Core Problem

The primary challenge was to find a reliable way to serialize the `com.google.genai.types.FunctionDeclaration` and `com.google.genai.types.Schema` objects into a human-readable JSON format for debugging and verification. Initial attempts using standard `Gson` or even the `com.google.protobuf.util.JsonFormat` utility resulted in empty or incorrect JSON, which was highly misleading.

## 2. Key Findings & Technical Details

- **It's Not Protobuf:** The user-facing `com.google.genai.types` library's objects (like `Schema`, `Content`, `Part`, etc.) are **not** simple POJOs, nor are they direct Protobuf `Message` objects. They are high-level, immutable Java objects implemented using Google's `@AutoValue`.

- **Jackson is the Key:** These `@AutoValue` classes extend a `JsonSerializable` base class and are heavily annotated with Jackson annotations (`@JsonProperty`, `@JsonDeserialize`). This indicates that the library's native, intended serialization mechanism is Jackson.

- **The Correct Tool: `.toJson()`:** The definitive, official, and only reliable way to serialize these specific objects to JSON is by calling the **`.toJson()`** method that is available on them (inherited from `JsonSerializable`). This method uses the library's internal, correctly configured Jackson `ObjectMapper` to produce the correct output.

- **The `JsonFormat` Misconception:** The `com.google.protobuf.util.JsonFormat` utility is designed for low-level Protobuf `Message` classes (likely found in packages like `com.google.genai.v1beta`), not the high-level `types` objects. Attempting to use it on the `types` objects will fail because they are not the classes it was designed for.

## 3. Test Case: `Tree` and `TreeNode`

To validate the schema generation logic, we used a recursive data structure:

-   **`Tree.java`**: Contains a `String name` and a `TreeNode root`.
-   **`TreeNode.java`**: Contains a `String data` and a `List<TreeNode> children`.

The `GeminiAdapter.getGeminiSchema(Tree.class)` method was used to generate the schema. The final, correct test involved calling `.toJson()` on the resulting `Schema` object, which produced a perfect JSON string, correctly representing the `items` of the `children` array and handling the recursion gracefully.

## 4. Conclusion

Any work involving the direct manipulation or inspection of `com.google.genai.types` objects that require serialization to JSON **must** use the object's own **`.toJson()`** method. Standard JSON libraries used directly will likely fail, and Protobuf-specific tools like `JsonFormat` are incorrect for this layer of the library. This knowledge is critical for any future debugging or extension of the function schema generation system.

## 5. CRITICAL BUG: Incomplete & Inaccurate Parameter Schemas (Discovered 2025-11-16)

A critical, high-priority bug has been identified in the `ToolManager`'s `FunctionDeclaration` generation process. This bug severely impacts the AI's ability to correctly understand and use tools.

The issues are threefold:
1.  **Missing `@AiToolParam` Descriptions:** The `description` field from the `@AiToolParam` annotation is not being parsed or included in the final schema for tool parameters. This leaves the AI without crucial context on what each parameter is for.
2.  **No Rich Parameter Schemas:** While return types are processed by `SchemaProvider2` to generate rich, detailed schemas, this same logic is not being applied to the input parameters of a tool method. Parameters are receiving a basic, type-only schema.
3.  **Getter Mismatch:** The schema generation for return types is incorrectly including properties based on public getter methods (e.g., `isAccepted()` from `SuggestChangeResult`) that do not exist as fields in the serialized JSON response. This creates a discrepancy where the schema describes fields that the AI will never receive, leading to confusion and potential errors.

**This is the current highest priority bug to be fixed.**

---
**2025-11-16:** The investigation is complete. The V2 engine (`SchemaProvider2` + `GeminiAdapter`) is a success. The core finding was that `com.google.genai.types` objects must be serialized using their built-in `.toJson()` method, as they are Jackson-based, not Protobuf-based. While the new engine is now active in `ToolManager`, we are leaving the old `GeminiSchemaGenerator` in place for a short period as a safeguard.
