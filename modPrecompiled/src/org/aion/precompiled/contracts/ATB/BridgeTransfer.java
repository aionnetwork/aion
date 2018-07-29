package org.aion.precompiled.contracts.ATB;

import org.aion.base.util.ByteUtil;
import org.aion.precompiled.PrecompiledUtilities;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public class BridgeTransfer {

    /**
     * Consists of the sourceTransactionHash (32 bytes), the recipient address (32 bytes)
     * and the value (padded to 32 bytes).
     */
    static final int TRANSFER_SIZE = 32 + 32 + 32;

    private final BigInteger transferValue;
    private final byte[] recipient;
    private final byte[] sourceTransactionHash;

    BridgeTransfer(@Nonnull final BigInteger transferValue,
                   @Nonnull final byte[] recipient,
                   @Nonnull final byte[] sourceTransactionHash) {
        this.transferValue = transferValue;
        this.recipient = recipient;
        this.sourceTransactionHash = sourceTransactionHash;
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
