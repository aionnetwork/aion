package org.aion.precompiled.contracts.ATB;

import java.util.Arrays;
import org.aion.precompiled.PrecompiledUtilities;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public class BridgeTransfer {

    /**
     * Consists of the sourceTransactionHash (32 bytes), the recipient address (32 bytes)
     * and the value (padded to 16 bytes).
     */
    static final int TRANSFER_SIZE = 32 + 32 + 16;

    private final BigInteger transferValue;
    private final byte[] recipient;
    private final byte[] sourceTransactionHash;

    BridgeTransfer(@Nonnull final BigInteger transferValue,
                   @Nonnull final byte[] recipient,
                   @Nonnull final byte[] sourceTransactionHash) {
        if (transferValue.toByteArray().length > 16)
            throw new IllegalArgumentException("transferValue to byte array exceeds maximum size.");
        this.transferValue = transferValue;
        this.recipient = recipient.length == 32 ? recipient : Arrays.copyOf(recipient, 32);
        this.sourceTransactionHash =
            sourceTransactionHash.length == 32
                ? sourceTransactionHash
                : Arrays.copyOf(sourceTransactionHash, 32);
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
