package org.aion.crypto;

import java.math.BigInteger;

public interface ECKey {

    ECKey fromPrivate(BigInteger privKey);

    ECKey fromPrivate(byte[] bs);

    byte[] computeAddress(byte[] pubBytes);

    byte[] getAddress();

    byte[] getPubKey();

    ISignature sign(byte[] msg);

    byte[] getPrivKeyBytes();

    /**
     * An exception when the ECKey couldn't be used to sign messages because the private key is
     * missing.
     */
    class MissingPrivateKeyException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public MissingPrivateKeyException() {}

        public MissingPrivateKeyException(String msg) {
            super(msg);
        }

        public MissingPrivateKeyException(Throwable cause) {
            super(cause);
        }

        public MissingPrivateKeyException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
