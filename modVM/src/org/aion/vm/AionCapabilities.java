package org.aion.vm;

import org.aion.avm.core.AvmTransaction;
import org.aion.avm.core.IExternalCapabilities;
import org.aion.avm.core.types.InternalTransaction;
import org.aion.crypto.HashUtil;
import org.aion.types.AionAddress;

public class AionCapabilities implements IExternalCapabilities {

    @Override
    public byte[] sha256(byte[] bytes) {
        return HashUtil.sha256(bytes);
    }

    @Override
    public byte[] blake2b(byte[] bytes) {
        return org.aion.crypto.hash.Blake2bNative.blake256(bytes);
    }

    @Override
    public byte[] keccak256(byte[] bytes) {
        return HashUtil.keccak256(bytes);
    }

    @Override
    public boolean verifyEdDSA(byte[] bytes, byte[] bytes1, byte[] bytes2) {
        return org.aion.crypto.ed25519.ECKeyEd25519.verify(bytes, bytes1, bytes2);
    }

    @Override
    public AionAddress generateContractAddress(AvmTransaction tx) {
        return new AionAddress(HashUtil.calcNewAddr(tx.senderAddress.toByteArray(), tx.nonce.toByteArray()));
    }

    @Override
    public AionAddress generateContractAddress(InternalTransaction tx) {
        return new AionAddress(HashUtil.calcNewAddr(tx.getSenderAddress().toByteArray(), tx.getNonce()));
    }
}
