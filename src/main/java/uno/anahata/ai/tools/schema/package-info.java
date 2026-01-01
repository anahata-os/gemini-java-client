/* Licensed under the Apache License, Version 2.0 */
/**
 * Provides the logic for generating JSON schemas from Java classes and methods.
 * <p>
 * This package contains the {@link uno.anahata.ai.functions.schema.GeminiSchemaGenerator},
 * which is responsible for introspecting Java types, methods, and annotations to produce
 * the JSON schema definition that the Google Gemini API requires for function calling.
 * It translates Java types into their corresponding schema types (e.g., String, Integer, Object, Array)
 * and uses annotations for descriptions and constraints.
 */
package uno.anahata.ai.tools.schema;