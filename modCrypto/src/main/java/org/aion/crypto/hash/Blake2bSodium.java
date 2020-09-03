package org.aion.crypto.hash;

import org.aion.util.file.NativeLoader;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

public class Blake2bSodium {
    final static int blake2bOutputLength = 32;
    static {
        NativeLoader.loadLibrary("sodium");
        NaCl.sodium();
    }

    public static byte[] blake256(byte[] message) {
        byte[] buffer = new byte[blake2bOutputLength];
        Sodium.crypto_generichash_blake2b(buffer, blake2bOutputLength, message, message.length, new byte[0], 0);
        return buffer;
    }
}
