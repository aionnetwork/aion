/*******************************************************************************
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
 *
 ******************************************************************************/
package org.aion.crypto;

/**
 * The interface for EC signature.
 *
 * @author jin, cleaned by yulong
 */
public interface ISignature {

    /**
     * Converts into a byte array.
     *
     * @return
     */
    byte[] toBytes();

    /**
     * Returns the raw signature.
     */
    byte[] getSignature();

    /**
     * Returns the public key, encoded or recovered.
     *
     * @param msg
     *            Only required by Secp256k1; pass null if you're using ED25519
     * @return
     */
    byte[] getPubkey(byte[] msg);

    /**
     * Recovers the address of the account given the signature
     */
    byte[] getAddress();
}
