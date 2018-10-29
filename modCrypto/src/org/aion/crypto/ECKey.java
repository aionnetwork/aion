/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
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
