package org.aion.precompiled.contracts.ATB;

import com.google.common.annotations.VisibleForTesting;
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

    private BridgeTransfer(
            @Nonnull final BigInteger transferValue,
            @Nonnull final byte[] recipient,
            @Nonnull final byte[] sourceTransactionHash) {
        this.transferValue = transferValue;
        this.recipient = recipient.length == 32 ? recipient : Arrays.copyOf(recipient, 32);
        this.sourceTransactionHash =
                sourceTransactionHash.length == 32
                        ? sourceTransactionHash
                        : Arrays.copyOf(sourceTransactionHash, 32);
    }

    @VisibleForTesting
    public static BridgeTransfer getInstance(
        @Nonnull final BigInteger transferValue,
        @Nonnull final byte[] recipient,
        @Nonnull final byte[] sourceTransactionHash) {
        if (transferValue.toByteArray().length > 16) return null;
        return new BridgeTransfer(transferValue, recipient, sourceTransactionHash);
    }

    @VisibleForTesting
    public byte[] getRecipient() {
        return this.recipient;
    }

    @VisibleForTesting
    public byte[] getSourceTransactionHash() {
        return this.sourceTransactionHash;
    }

    @VisibleForTesting
    public byte[] getTransferValueByteArray() {
        return PrecompiledUtilities.pad(transferValue.toByteArray(), 16);
    }

    @VisibleForTesting
    public BigInteger getTransferValue() {
        return this.transferValue;
    }
}
