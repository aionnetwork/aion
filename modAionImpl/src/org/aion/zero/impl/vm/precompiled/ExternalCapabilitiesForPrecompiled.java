package org.aion.zero.impl.vm.precompiled;

import org.aion.base.TxUtil;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.crypto.hash.Blake2bNative;
import org.aion.fastvm.IExternalCapabilities;
import org.aion.precompiled.type.IExternalCapabilitiesForPrecompiled;

/**
 * An implementation of the {@link IExternalCapabilities} interface for Precompiled.
 */
public final class ExternalCapabilitiesForPrecompiled implements IExternalCapabilitiesForPrecompiled {

    @Override
    public boolean verifyISig(byte[] hash, byte[] signature) {
        ISignature sig = SignatureFac.fromBytes(signature);
        return SignatureFac.verify(hash, sig);
    }

    @Override
    public byte[] blake2b(byte[] bytes) {
        return Blake2bNative.blake256(bytes);
    }

    @Override
    public byte[] keccak256(byte[] bytes) {
        return HashUtil.keccak256(bytes);
    }

    @Override
    public boolean verifyEd25519Signature(byte[] bytes, byte[] signature, byte[] pubKey) {
        return ECKeyEd25519.verify(bytes, signature, pubKey);
    }

    @Override
    public boolean verifyEd25519Signature(byte[] bytes, byte[] signature) {
        Ed25519Signature sig = Ed25519Signature.fromBytes(signature);
        if (sig == null) return false;

        return verifyEd25519Signature(bytes, sig.getSignature(), sig.getPubkey(null));
    }

    @Override
    public byte[] getEd25519Address(byte[] signature) {
        Ed25519Signature sig = Ed25519Signature.fromBytes(signature);
        return sig == null ? null : sig.getAddress();
    }

    @Override
    public byte[] getISigAddress(byte[] signature) {
        return SignatureFac.fromBytes(signature).getAddress();
    }

    @Override
    public long calculateTransactionCost(byte[] data, boolean isCreate) {
        return TxUtil.calculateTransactionCost(data, isCreate);
    }
}
