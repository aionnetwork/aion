package org.aion.precompiled.encoding;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.PrecompiledUtilities;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ThreadSafe
public class AbiEncoder {
    private List<BaseTypeFVM> params;
    private volatile StringBuffer buffer;
    private String signature;

    private static final int STATIC_OFFSET_LEN = 16;

    public AbiEncoder(@Nonnull final byte[] signature, @Nonnull BaseTypeFVM ...params) {
        this(ByteUtil.toHexString(signature), params);
    }

    public AbiEncoder(@Nonnull String signature, @Nonnull BaseTypeFVM ...params) {
        this.params = new ArrayList<>(Arrays.asList(params));
        this.signature = signature;
    }

    public synchronized AbiEncoder setParam(BaseTypeFVM param) {
        this.params.add(param);
        return this;
    }

    private synchronized void createBuffer() {
        // represents the offsets until dynamic parameters
        int offset = 0;
        final StringBuffer b = new StringBuffer();

        b.append(ByteUtil.toHexString(encodeSignature(this.signature)));

        for (BaseTypeFVM type : this.params) {
            if (type.isDynamic()) {
                offset += 16;
            } else {
                offset += type.serialize().length;
            }
        }

        // second iteration, go through each element assembling
        for (BaseTypeFVM type : this.params) {
            if (type.isDynamic()) {
                byte[] off = PrecompiledUtilities.pad(BigInteger.valueOf(offset).toByteArray(), 16);
                b.append(ByteUtil.toHexString(off));
                offset += type.serialize().length;
            } else {
                b.append(ByteUtil.toHexString(type.serialize()));
            }
        }

        // in the last iteration just iterate through dynamic elements
        for (BaseTypeFVM type : this.params) {
            if (type.isDynamic()) {
                b.append(ByteUtil.toHexString(type.serialize()));
            }
        }

        this.buffer = b;
    }

    public String encode() {
        if (buffer == null)
            createBuffer();
        return "0x" + buffer.toString();
    }

    public byte[] encodeBytes() {
        encode();
        return ByteUtil.hexStringToBytes(buffer.toString());
    }

    @Override
    public String toString() {
        return encode();
    }

    private static byte[] encodeSignature(String s) {
        // encode signature
        byte[] sig = new byte[4];
        System.arraycopy(HashUtil.keccak256(s.getBytes()), 0, sig, 0, 4);
        return sig;
    }
}
