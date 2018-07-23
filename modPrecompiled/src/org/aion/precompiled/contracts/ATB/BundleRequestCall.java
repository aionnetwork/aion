package org.aion.precompiled.contracts.ATB;

import javax.annotation.Nonnull;

class BundleRequestCall {
    public final byte[] blockHash;
    public final BridgeBundle[] bundles;
    public final byte[][] signatures;

    public BundleRequestCall(@Nonnull final byte[] blockHash,
                             @Nonnull final BridgeBundle[] bundles,
                             @Nonnull final byte[][] signatures) {
        this.blockHash = blockHash;
        this.bundles = bundles;
        this.signatures = signatures;
    }
}
