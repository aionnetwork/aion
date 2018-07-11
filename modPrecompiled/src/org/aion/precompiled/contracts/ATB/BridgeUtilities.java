package org.aion.precompiled.contracts.ATB;

import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.DataWord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Collection of minimal utilities used by the bridge contract. Some code
 * is redundant/isolated for purposes of easy auditing
 */
public class BridgeUtilities {
    public static final byte[] EMPTY = new byte[0];

    static byte[] getAddress(@Nullable byte[] addr) {
        if (addr == null)
            return null;

        if (addr.length < 20)
            return null;

        byte[] out = new byte[20];
        System.arraycopy(addr, addr.length - 20, out, 0, 20);
        return addr;
    }

    static byte[] toSignature(@Nonnull final String funcSignature) {
        byte[] sigChopped = new byte[4];
        byte[] full = HashUtil.keccak256(funcSignature.getBytes());
        System.arraycopy(full, 0, sigChopped, 0, 4);
        return sigChopped;
    }

    static byte[] getSignature(@Nonnull final byte[] input) {
        if (input.length < 4)
            return null;

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
}
