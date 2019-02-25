package org.aion.crypto;

/** @author jin */
public interface Hash256 {

    byte[] h256(byte[] in);

    byte[] h256(byte[] in1, byte[] in2);
}
