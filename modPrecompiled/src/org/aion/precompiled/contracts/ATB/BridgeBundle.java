package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public class BridgeBundle {
    public final BigInteger transferValue;
    public final byte[] recipient;
    public final byte[] data;

    public BridgeBundle(@Nonnull final BigInteger transferValue,
                        @Nonnull final byte[] recipient,
                        @Nonnull final byte[] data) {
        this.transferValue = transferValue;
        this.recipient = recipient;
    }
}
