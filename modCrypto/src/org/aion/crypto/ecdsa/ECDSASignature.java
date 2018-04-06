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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.crypto.ecdsa;

import static org.aion.base.util.BIUtil.isLessThan;
import static org.aion.base.util.ByteUtil.bigIntegerToBytes;
import static org.aion.crypto.ecdsa.ECKeySecp256k1.CURVE;
import static org.aion.crypto.ecdsa.ECKeySecp256k1.ETH_SECP256K1N;
import static org.aion.crypto.ecdsa.ECKeySecp256k1.HALF_CURVE_ORDER;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.crypto.ISignature;
import org.spongycastle.asn1.ASN1InputStream;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.DLSequence;
import org.spongycastle.util.encoders.Base64;

/**
 *
 * 
 */
public class ECDSASignature implements ISignature {

    /**
     * The two components of the signature.
     */
    public final BigInteger r, s;
    public byte v;
    private static int LEN = 65;

    public static ECDSASignature fromBytes(byte[] sigs) {
        if (sigs != null && sigs.length == LEN) {
            return ECDSASignature.fromComponents(sigs);
        } else {
            System.err.println("ECDSA signature decode failed!");
            return null;
        }
    }

    @Override
    public byte[] toBytes() {
        ByteBuffer bb = ByteBuffer.allocate(LEN);
        bb.put(v);
        bb.put(bigIntegerToBytes(r, 32));
        bb.put(bigIntegerToBytes(s, 32));
        return bb.array();
    }

    @Override
    public byte[] getSignature() {
        return toBytes();
    }

    @Override
    public byte[] getPubkey(byte[] msg) {
         return new ECKeySecp256k1().recoverFromSignature(this.v - 27, this, msg).getPubKey();
    }

    /**
     * Constructs a signature with the given components. Does NOT automatically
     * canonicalise the signature.
     *
     * @param r
     * @param s
     */
    public ECDSASignature(BigInteger r, BigInteger s) {
        this.r = r;
        this.s = s;
    }

    /**
     * t
     *
     * @param r
     * @param s
     * @return
     */
    private static ECDSASignature fromComponents(byte[] r, byte[] s) {
        return new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));
    }

    /**
     * mix was in structure { v, r, s }
     *
     * @param mix
     * @return
     */
    public static ECDSASignature fromComponents(byte[] mix) {
        BigInteger r = new BigInteger(1, mix, 1, 32);
        BigInteger s = new BigInteger(1, mix, 33, 32);
        ECDSASignature signature = new ECDSASignature(r, s);
        signature.v = mix[0];
        return signature;
    }

    /**
     *
     * @param r
     * @param s
     * @param v
     * @return
     */
    public static ECDSASignature fromComponents(byte[] r, byte[] s, byte v) {
        ECDSASignature signature = fromComponents(r, s);
        signature.v = v;
        return signature;
    }

    public boolean validateComponents() {
        return validateComponents(r, s, v);
    }

    public static boolean validateComponents(BigInteger r, BigInteger s, byte v) {

        if (v != 27 && v != 28) {
            return false;
        }

        if (isLessThan(r, BigInteger.ONE)) {
            return false;
        }
        if (isLessThan(s, BigInteger.ONE)) {
            return false;
        }

        if (!isLessThan(r, ETH_SECP256K1N)) {
            return false;
        }
        return isLessThan(s, ETH_SECP256K1N);
    }

    public static ECDSASignature decodeFromDER(byte[] bytes) {
        ASN1InputStream decoder = null;
        try {
            decoder = new ASN1InputStream(bytes);
            DLSequence seq = (DLSequence) decoder.readObject();
            if (seq == null) {
                throw new RuntimeException("Reached past end of ASN.1 stream.");
            }
            ASN1Integer r, s;
            try {
                r = (ASN1Integer) seq.getObjectAt(0);
                s = (ASN1Integer) seq.getObjectAt(1);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException(e);
            }
            // OpenSSL deviates from the DER spec by interpreting these values
            // as unsigned, though they should not be
            // Thus, we always use the positive versions. See:
            // http://r6.ca/blog/20111119T211504Z.html
            return new ECDSASignature(r.getPositiveValue(), s.getPositiveValue());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (decoder != null) {
                try {
                    decoder.close();
                } catch (IOException x) {
                }
            }
        }
    }

    /**
     * Will automatically adjust the S component to be less than or equal to
     * half the curve order, if necessary. This is required because for every
     * signature (r,s) the signature (r, -s (mod N)) is a valid signature of the
     * same message. However, we dislike the ability to modify the bits of a
     * Ethereum transaction after it's been signed, as that violates various
     * assumed invariants. Thus in future only one of those forms will be
     * considered legal and the other will be banned.
     *
     * @return -
     */
    public ECDSASignature toCanonicalised() {
        if (s.compareTo(HALF_CURVE_ORDER) > 0) {
            // The order of the curve is the number of valid points that exist
            // on that curve. If S is in the upper
            // half of the number of valid points, then bring it back to the
            // lower half. Otherwise, imagine that
            // N = 10
            // s = 8, so (-8 % 10 == 2) thus both (r, 8) and (r, 2) are valid
            // solutions.
            // 10 - 8 == 2, giving us always the latter solution, which is
            // canonical.
            return new ECDSASignature(r, CURVE.getN().subtract(s));
        } else {
            return this;
        }
    }

    /**
     *
     * @return -
     */
    public String toBase64() {
        byte[] sigData = new byte[65]; // 1 header + 32 bytes for R + 32 bytes
                                       // for S
        sigData[0] = v;
        System.arraycopy(bigIntegerToBytes(this.r, 32), 0, sigData, 1, 32);
        System.arraycopy(bigIntegerToBytes(this.s, 32), 0, sigData, 33, 32);
        return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
    }

    public byte[] toByteArray() {
        final byte fixedV = this.v >= 27 ? (byte) (this.v - 27) : this.v;

        return ByteUtil.merge(ByteUtil.bigIntegerToBytes(this.r), ByteUtil.bigIntegerToBytes(this.s),
                new byte[] { fixedV });
    }

    public String toHex() {
        return Hex.toHexString(toByteArray());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ECDSASignature signature = (ECDSASignature) o;

        if (!r.equals(signature.r)) {
            return false;
        }
        return s.equals(signature.s);
    }

    @Override
    public int hashCode() {
        int result = r.hashCode();
        result = 31 * result + s.hashCode();
        return result;
    }

    /**
     * Throws unsupported operation for now since we don't yet
     * have a definition/support/procedure for what ECDSA keys
     * should look like.
     */
    @Override
    public byte[] getAddress() {
        throw new UnsupportedOperationException("Address definition not specified for ECDSA keys");
    }
}