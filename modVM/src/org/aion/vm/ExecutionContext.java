/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.vm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.aion.base.type.AionAddress;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.DataWordStub;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionSideEffects;

/**
 * Execution context, including both transaction and block information.
 *
 * @author yulong
 */
public class ExecutionContext implements TransactionContext {
    private static final int ENCODE_BASE_LEN =
            (AionAddress.SIZE * 4)
                    + (DataWord.BYTES * 3)
                    + (Long.BYTES * 4)
                    + (Integer.BYTES * 4);
    public static int CALL = 0;
    public static int DELEGATECALL = 1;
    public static int CALLCODE = 2;
    public static int CREATE = 3;

    private SideEffects sideEffects;
    private Address origin;
    private byte[] originalTxHash;

    public Address address;
    public Address sender;
    private Address blockCoinbase;
    private DataWordStub nrgPrice;
    private DataWordStub callValue;
    private DataWordStub blockDifficulty;
    private byte[] callData;
    private byte[] txHash;
    private long nrgLimit; // NOTE: nrg_limit = tx_nrg_limit - tx_basic_cost
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;
    private int depth;
    private int kind;
    private int flags;

    /**
     * Creates a VM execution context.
     *
     * @param txHash The transaction hash
     * @param destination The transaction address.
     * @param origin The sender of the original transaction.
     * @param sender The transaction caller.
     * @param nrgPrice The nrg price in current environment.
     * @param nrgLimit The nrg limit in current environment.
     * @param callValue The deposited value by instruction/trannsaction.
     * @param callData The call data.
     * @param depth The execution stack depth.
     * @param kind The transaction kind.
     * @param flags The transaction flags.
     * @param blockCoinbase The beneficiary of the block.
     * @param blockNumber The block number.
     * @param blockTimestamp The block timestamp.
     * @param blockNrgLimit The block energy limit.
     * @param blockDifficulty The block difficulty.
     * @throws IllegalArgumentException if any numeric quantities are negative or txHash is not
     *     length 32.
     */
    public ExecutionContext(
            byte[] txHash,
            Address destination,
            Address origin,
            Address sender,
            DataWordStub nrgPrice,
            long nrgLimit,
            DataWordStub callValue,
            byte[] callData,
            int depth,
            int kind,
            int flags,
            Address blockCoinbase,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            DataWordStub blockDifficulty) {

        super();

        this.address = destination;
        this.origin = origin;
        this.sender = sender;
        this.nrgPrice = nrgPrice;
        this.nrgLimit = nrgLimit;
        this.callValue = callValue;
        this.callData = callData;
        this.depth = depth;
        this.kind = kind;
        this.flags = flags;
        this.blockCoinbase = blockCoinbase;
        this.blockNumber = blockNumber;
        this.blockTimestamp = blockTimestamp;
        this.blockNrgLimit = blockNrgLimit;
        this.blockDifficulty = blockDifficulty;
        this.txHash = txHash;
        this.originalTxHash = txHash;

        this.sideEffects = new SideEffects();
    }

    /**
     * Returns a big-endian binary encoding of this ExecutionContext in the following format:
     *
     * <p>|32b - address|32b - origin|32b - caller|16b - nrgPrice|8b - nrgLimit|16b - callValue| 4b
     * - callDataLength|?b - callData|4b - depth|4b - kind|4b - flags|32b - blockCoinbase| 8b -
     * blockNumber|8b - blockTimestamp|8b - blockNrgLimit|16b - blockDifficulty|
     *
     * <p>where callDataLength is the length of callData.
     *
     * @return a binary encoding of this ExecutionContext.
     */
    @Override
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getEncodingLength());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(address.toBytes());
        buffer.put(origin.toBytes());
        buffer.put(sender.toBytes());
        buffer.put(nrgPrice.getData());
        buffer.putLong(nrgLimit);
        buffer.put(callValue.getData());
        buffer.putInt(callData.length); // length of the call data
        buffer.put(callData);
        buffer.putInt(depth);
        buffer.putInt(kind);
        buffer.putInt(flags);
        buffer.put(blockCoinbase.toBytes());
        buffer.putLong(blockNumber);
        buffer.putLong(blockTimestamp);
        buffer.putLong(blockNrgLimit);
        buffer.put(blockDifficulty.getData());
        return buffer.array();
    }

    /** @return the transaction hash. */
    @Override
    public byte[] getTransactionHash() {
        return txHash;
    }

    @Override
    public void setDestinationAddress(Address address) {
        this.address = address;
    }

    /** @return the transaction address. */
    @Override
    public Address getDestinationAddress() {
        return address;
    }

    /** @return the origination address, which is the sender of original transaction. */
    @Override
    public Address getOriginAddress() {
        return origin;
    }

    /** @return the transaction caller. */
    @Override
    public Address getSenderAddress() {
        return sender;
    }

    /** @return the nrg price in current environment. */
    @Override
    public DataWordStub getTransactionEnergyPrice() {
        return nrgPrice;
    }

    /** @return the nrg limit in current environment. */
    public long getTransactionEnergyLimit() {
        return nrgLimit;
    }

    /** @return the deposited value by instruction/trannsaction. */
    @Override
    public DataWordStub getTransferValue() {
        return callValue;
    }

    /** @return the call data. */
    @Override
    public byte[] getTransactionData() {
        return callData;
    }

    /** @return the execution stack depth. */
    @Override
    public int getTransactionStackDepth() {
        return depth;
    }

    /** @return the transaction kind. */
    @Override
    public int getTransactionKind() {
        return kind;
    }

    /** @return the transaction flags. */
    @Override
    public int getFlags() {
        return flags;
    }

    /** @return the block's beneficiary. */
    @Override
    public Address getMinerAddress() {
        return blockCoinbase;
    }

    /** @return the block number. */
    @Override
    public long getBlockNumber() {
        return blockNumber;
    }

    /** @return the block timestamp. */
    @Override
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    /** @return the block energy limit. */
    @Override
    public long getBlockEnergyLimit() {
        return blockNrgLimit;
    }

    /** @return the block difficulty. */
    @Override
    public DataWordStub getBlockDifficulty() {
        return blockDifficulty;
    }

    /** @return the transaction helper. */
    @Override
    public TransactionSideEffects getSideEffects() {
        return sideEffects;
    }

    /**
     * Sets the transaction hash to txHash.
     *
     * @param txHash The new transaction hash.
     */
    @Override
    public void setTransactionHash(byte[] txHash) {
        this.txHash = txHash;
    }

    /**
     * Returns the length of the big-endian binary encoding of this ExecutionContext.
     *
     * @return the legtn of this ExecutionContext's binary encoding.
     */
    private int getEncodingLength() {
        return ENCODE_BASE_LEN + callData.length;
    }

    /** @return the original transaction hash. */
    @Override
    public byte[] getHashOfOriginTransaction() {
        return originalTxHash;
    }
}
