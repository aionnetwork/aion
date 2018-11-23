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

package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.PrecompiledUtilities;

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
