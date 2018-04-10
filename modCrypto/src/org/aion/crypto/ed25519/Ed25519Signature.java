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
package org.aion.crypto.ed25519;

import org.aion.base.util.ByteUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ISignature;

import java.util.Arrays;

/**
 * ED25519 signature implementation. Each {@link Ed25519Signature} contains two
 * components, public key and raw signature.
 *
 * @author yulong
 */
public class Ed25519Signature implements ISignature {

    private static final int LEN = ECKeyEd25519.PUBKEY_BYTES + ECKeyEd25519.SIG_BYTES;

    private byte[] pk;

    private byte[] sig;

    public Ed25519Signature(byte[] pk, byte[] sig) {
        this.pk = pk;
        this.sig = sig;
    }

    public static Ed25519Signature fromBytes(byte[] args) {
        if (args != null && args.length == LEN) {
            byte[] pk = Arrays.copyOfRange(args, 0, ECKeyEd25519.PUBKEY_BYTES);
            byte[] sig = Arrays.copyOfRange(args, ECKeyEd25519.PUBKEY_BYTES, LEN);
            return new Ed25519Signature(pk, sig);
        } else {
            System.err.println("Ed25519 signature decode failed!");
            return null;
        }
    }

    @Override
    public byte[] toBytes() {
        byte[] buf = new byte[LEN];
        System.arraycopy(pk, 0, buf, 0, ECKeyEd25519.PUBKEY_BYTES);
        System.arraycopy(sig, 0, buf, ECKeyEd25519.PUBKEY_BYTES, ECKeyEd25519.SIG_BYTES);

        return buf;
    }

    @Override
    public byte[] getSignature() {
        return sig;
    }

    @Override
    public byte[] getPubkey(byte[] msg) {
        return pk;
    }

    @Override
    public String toString() {

        byte[] address = this.getAddress();

        return "[pk: " + (this.pk == null ? "null" : ByteUtil.toHexString(this.pk)) +
                " address: " + (address == null ? "null" : ByteUtil.toHexString(address)) +
                 " signature: " +  (this.sig == null ? "null" : ByteUtil.toHexString(this.sig)) + "]";
    }

    @Override
    public byte[] getAddress() {
        if (this.pk == null)
            return null;
        return AddressSpecs.computeA0Address(this.pk);
    }
}
