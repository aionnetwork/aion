package org.aion.base.vm;

/** @author jay */
public interface IDataWord {

    /**
     * Returns the byte array data the IDataWord wraps.
     *
     * @return the byte array data the IDataWord wraps.
     */
    byte[] getData();

    /**
     * Returns the underlying byte array, truncated so that leading zero bytes are removed.
     *
     * @return a truncated underlying byte array with all leading zeros removed.
     */
    byte[] getNoLeadZeroesData();

    /**
     * Returns a copy of the IDataWord.
     *
     * @return a cope of the IDataWord.
     */
    IDataWord copy();

    /**
     * Returns true only if the underlying byte array consists only of zero bytes.
     *
     * @apiNote An IDataWord whose underlying byte array consists only of zero bytes is interpreted
     *     as null by the database.
     * @return true only if the IDataWord consists of zero bytes.
     */
    boolean isZero();
}
