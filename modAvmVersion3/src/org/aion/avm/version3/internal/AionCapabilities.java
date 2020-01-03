package org.aion.avm.version3.internal;

import org.aion.avm.core.IExternalCapabilities;
import org.aion.base.InvokableTxUtil;
import org.aion.base.TxUtil;
import org.aion.crypto.HashUtil;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;

import java.math.BigInteger;

public final class AionCapabilities implements IExternalCapabilities {

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
    public AionAddress generateContractAddress(AionAddress deployerAddress, BigInteger nonce) {
        return TxUtil.calculateContractAddress(deployerAddress.toByteArray(), nonce);
    }

    @Override
    public InternalTransaction decodeSerializedTransaction(byte[] transactionPayload, AionAddress executor, long energyPrice, long energyLimit) {
        return InvokableTxUtil.decode(transactionPayload, executor, energyPrice, energyLimit);
    }
}
