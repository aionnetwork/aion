package org.aion.precompiled.contracts.ATB;

import org.aion.base.util.ByteArrayWrapper;

import javax.annotation.Nonnull;

import static org.aion.precompiled.contracts.ATB.BridgeUtilities.*;

enum BridgeEventSig {
    CHANGE_OWNER        ("changedOwner(address)"),
    ADD_MEMBER          ("addMember(address)"),
    REMOVE_MEMBER       ("removeMember(address)"),
    PROCESSED_BUNDLE    ("processedBundle(bytes32,bytes32)"),
    DISTRIBUTED         ("distributed(address,uint128)");

    private final byte[] hashed;
    BridgeEventSig(@Nonnull final String eventSignature) {
        this.hashed = toEventSignature(eventSignature);
    }

    public byte[] getHashed() {
        return this.hashed;
    }
}
