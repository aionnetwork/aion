package org.aion.precompiled.contracts.ATB;

import org.aion.base.util.ByteArrayWrapper;

import javax.annotation.Nonnull;

import static org.aion.precompiled.contracts.ATB.BridgeUtilities.*;

enum BridgeEventSig {
    CHANGE_OWNER        ("ChangedOwner(getRecipient)"),
    ADD_MEMBER          ("AddMember(getRecipient)"),
    REMOVE_MEMBER       ("RemoveMember(getRecipient)"),
    PROCESSED_BUNDLE    ("ProcessedBundle(bytes32,bytes32)"),
    DISTRIBUTED         ("Distributed(getRecipient,uint128)");

    private final byte[] hashed;
    BridgeEventSig(@Nonnull final String eventSignature) {
        this.hashed = toEventSignature(eventSignature);
    }

    public byte[] getHashed() {
        return this.hashed;
    }
}
