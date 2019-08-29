package org.aion.db.store;

/**
 * Serializer interface.
 *
 * @param <T>
 * @param <S>
 */
public interface Serializer<T, S> {

    S serialize(T object);

    T deserialize(S stream);
}
