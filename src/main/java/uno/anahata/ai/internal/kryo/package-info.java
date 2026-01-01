/* Licensed under the Apache License, Version 2.0 */
/**
 * Contains custom serializers for the Kryo serialization framework.
 * <p>
 * This package provides specialized serializers to handle types that Kryo cannot
 * serialize by default or that require specific handling for efficiency. A key
 * example is the {@link uno.anahata.ai.internal.kryo.OptionalSerializer} for
 * correctly serializing {@link java.util.Optional} instances during session persistence.
 */
package uno.anahata.ai.internal.kryo;