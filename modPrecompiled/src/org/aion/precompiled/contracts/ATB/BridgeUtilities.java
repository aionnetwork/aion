package org.aion.precompiled.contracts.ATB;

import org.aion.mcf.vm.types.DataWord;

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
}
