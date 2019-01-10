package org.aion.base.util;

/** @author jin */
public interface Bytesable<T> {

    byte[] NULL_BYTE = new byte[] {(byte) 0x0};

    byte[] toBytes();

    T fromBytes(byte[] bs);
}
