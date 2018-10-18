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
import java.util.Arrays;
import javax.annotation.Nonnull;
import org.aion.precompiled.PrecompiledUtilities;

public class BridgeTransfer {

    /**
     * Consists of the sourceTransactionHash (32 bytes), the recipient address (32 bytes) and the
     * value (padded to 16 bytes).
     */
    static final int TRANSFER_SIZE = 32 + 32 + 16;

    private final BigInteger transferValue;
    private final byte[] recipient;
    private final byte[] sourceTransactionHash;

    private BridgeTransfer(@Nonnull final BigInteger transferValue,
        @Nonnull final byte[] recipient,
        @Nonnull final byte[] sourceTransactionHash) {
        this.transferValue = transferValue;
        this.recipient = recipient.length == 32 ? recipient : Arrays.copyOf(recipient, 32);
        this.sourceTransactionHash =
            sourceTransactionHash.length == 32
                ? sourceTransactionHash
                : Arrays.copyOf(sourceTransactionHash, 32);
    }

    static BridgeTransfer getInstance(@Nonnull final BigInteger transferValue,
        @Nonnull final byte[] recipient,
        @Nonnull final byte[] sourceTransactionHash) {
        if (transferValue.toByteArray().length > 16) {
            return null;
        }
        return new BridgeTransfer(transferValue, recipient, sourceTransactionHash);
    }

    byte[] getRecipient() {
        return this.recipient;
    }

    byte[] getSourceTransactionHash() {
        return this.sourceTransactionHash;
    }

    byte[] getTransferValueByteArray() {
        return PrecompiledUtilities.pad(transferValue.toByteArray(), 16);
    }

    BigInteger getTransferValue() {
        return this.transferValue;
    }
}
