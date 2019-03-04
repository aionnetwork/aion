package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.util.bytes.ByteUtil;

public class BridgeUtilities {

    static byte[] toSignature(@Nonnull final String funcSignature) {
        byte[] sigChopped = new byte[4];
        byte[] full = HashUtil.keccak256(funcSignature.getBytes());
        System.arraycopy(full, 0, sigChopped, 0, 4);
        return sigChopped;
    }

    static byte[] toEventSignature(@Nonnull final String eventSignature) {
        return HashUtil.keccak256(eventSignature.getBytes());
    }

    static byte[] getSignature(@Nonnull final byte[] input) {
        if (input.length < 4) return null;

        byte[] sig = new byte[4];
        System.arraycopy(input, 0, sig, 0, 4);
        return sig;
    }

    static byte[] orDefaultWord(@Nullable final byte[] input) {
        return input == null ? ByteUtil.EMPTY_HALFWORD : input;
    }

    static byte[] orDefaultDword(@Nullable final byte[] input) {
        return input == null ? ByteUtil.EMPTY_WORD : input;
    }

    private static final byte[] TRUE =
            ByteUtil.hexStringToBytes("00000000000000000000000000000001");

    static byte[] booleanToResultBytes(final boolean input) {
        return input ? TRUE : ByteUtil.EMPTY_HALFWORD;
    }

    static byte[] intToResultBytes(final int input) {
        return PrecompiledUtilities.pad(BigInteger.valueOf(input).toByteArray(), 16);
    }

    static byte[] computeBundleHash(byte[] sourceBlockHash, BridgeTransfer[] bundles) {
        int size = sourceBlockHash.length + bundles.length * BridgeTransfer.TRANSFER_SIZE;

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(sourceBlockHash);

        for (BridgeTransfer b : bundles) {
            buf.put(b.getSourceTransactionHash());
            buf.put(b.getRecipient());
            buf.put(b.getTransferValueByteArray());
        }

        return HashUtil.h256(buf.array());
    }
}
