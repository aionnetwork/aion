package org.aion.db.store;

/**
 * Serializer interface for representing objects as byte arrays for storage.
 *
 * @param <T> the class of objects used by a specific implementation
 * @apiNote There are no requirements on the implementation regarding how to handle {@code null}
 *     input for the defined methods. It is, therefore, recommended that the input be checked for
 *     {@code null} values before passing it to the methods to avoid {@link NullPointerException}s.
 */
public interface Serializer<T> {

    /**
     * Represents the given object as a byte array.
     *
     * @param object the object to be serialized
     * @return the byte array serialization for the given object
     */
    byte[] serialize(T object);

    /**
     * Interprets the given serialization as an object of type {@link T}.
     *
     * @param serialization the serialization to be decoded into an object of type {@link T}
     * @return an object of type {@link T} corresponding to the given serialization
     */
    T deserialize(byte[] serialization);
}
