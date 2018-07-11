package org.aion.precompiled.contracts.ATB;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public class BridgeBundle {
    public final BigInteger transferValue;
    public final byte[] recipient;

    public BridgeBundle(@Nonnull final BigInteger transferValue,
                        @Nonnull final byte[] recipient) {
        this.transferValue = transferValue;
        this.recipient = recipient;
    }
}
