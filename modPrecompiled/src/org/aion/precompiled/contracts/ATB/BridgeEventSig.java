package org.aion.precompiled.contracts.ATB;

import org.aion.base.util.ByteArrayWrapper;

import javax.annotation.Nonnull;

import static org.aion.precompiled.contracts.ATB.BridgeUtilities.*;

enum BridgeEventSig {
    CHANGE_OWNER        ("ChangedOwner(address)"),
    ADD_MEMBER          ("AddMember(address)"),
    REMOVE_MEMBER       ("RemoveMember(address)"),
    PROCESSED_BUNDLE    ("ProcessedBundle(bytes32,bytes32)"),
    DISTRIBUTED         ("Distributed(bytes32,address,uint128)"),
    SUCCESSFUL_TXHASH   ("SuccessfulTxHash(bytes32)");

    private final byte[] hashed;
    BridgeEventSig(@Nonnull final String eventSignature) {
        this.hashed = toEventSignature(eventSignature);
    }

    public byte[] getHashed() {
        return this.hashed;
    }
}
