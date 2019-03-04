package org.aion.vm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.aion.avm.core.IExternalCapabilities;
import org.aion.avm.core.NodeEnvironment;
import org.aion.crypto.HashUtil;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.TransactionInterface;

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
    public Address generateContractAddress(TransactionInterface tx) {
        Address sender = tx.getSenderAddress();
        long nonce = new BigInteger(tx.getNonce()).longValue();
        ByteBuffer buffer =
                ByteBuffer.allocate(Address.SIZE + Long.BYTES).put(sender.toBytes()).putLong(nonce);
        byte[] hash = sha256(buffer.array());
        hash[0] = NodeEnvironment.CONTRACT_PREFIX;
        return Address.wrap(hash);
    }
}