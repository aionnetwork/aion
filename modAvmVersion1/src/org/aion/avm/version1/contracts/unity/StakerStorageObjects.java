package org.aion.avm.version1.contracts.unity;

import avm.Address;
import org.aion.avm.userlib.AionBuffer;

import java.math.BigInteger;

public class StakerStorageObjects {

    static class AddressInfo {
        Address signingAddress;
        Address coinbaseAddress;
        long lastSigningAddressUpdate;

        protected AddressInfo(Address signingAddress, Address coinbaseAddress, long lastSigningAddressUpdate) {
            this.signingAddress = signingAddress;
            this.coinbaseAddress = coinbaseAddress;
            this.lastSigningAddressUpdate = lastSigningAddressUpdate;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH * 2 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(signingAddress);
            aionBuffer.putAddress(coinbaseAddress);
            aionBuffer.putLong(lastSigningAddressUpdate);
            return aionBuffer.getArray();
        }

        protected static AddressInfo from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new AddressInfo(buffer.getAddress(), buffer.getAddress(), buffer.getLong());
        }
    }

    static class PendingUnbond {
        Address recipient;
        BigInteger value;
        BigInteger fee;
        long blockNumber;

        protected PendingUnbond(Address recipient, BigInteger value, BigInteger fee, long blockNumber) {
            this.recipient = recipient;
            this.value = value;
            this.fee = fee;
            this.blockNumber = blockNumber;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH + 32 * 2 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(recipient);
            aionBuffer.put32ByteInt(value);
            aionBuffer.put32ByteInt(fee);
            aionBuffer.putLong(blockNumber);
            return aionBuffer.getArray();
        }

        protected static PendingUnbond from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new PendingUnbond(buffer.getAddress(), buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.getLong());
        }
    }

    static class PendingTransfer {
        Address initiator;
        Address toStaker;
        BigInteger value;
        BigInteger fee;
        long blockNumber;

        protected PendingTransfer(Address initiator, Address toStaker, BigInteger value, BigInteger fee, long blockNumber) {
            this.initiator = initiator;
            this.toStaker = toStaker;
            this.value = value;
            this.fee = fee;
            this.blockNumber = blockNumber;
        }

        protected byte[] serialize() {
            int length = Address.LENGTH * 3 + 32 * 2 + Long.BYTES;
            AionBuffer aionBuffer = AionBuffer.allocate(length);
            aionBuffer.putAddress(initiator);
            aionBuffer.putAddress(toStaker);
            aionBuffer.put32ByteInt(value);
            aionBuffer.put32ByteInt(fee);
            aionBuffer.putLong(blockNumber);
            return aionBuffer.getArray();
        }

        protected static PendingTransfer from(byte[] serializedBytes) {
            AionBuffer buffer = AionBuffer.wrap(serializedBytes);
            return new PendingTransfer(buffer.getAddress(), buffer.getAddress(), buffer.get32ByteInt(), buffer.get32ByteInt(), buffer.getLong());
        }
    }
}
