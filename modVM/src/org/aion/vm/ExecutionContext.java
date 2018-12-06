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

/**
 * Execution context, including both transaction and block information.
 *
 * @author yulong
 */
public class ExecutionContext {
    private static final int ENCODE_BASE_LEN =
            (AionAddress.SIZE * 4)
                    + (DataWord.BYTES * 3)
                    + (Long.BYTES * 4)
                    + (Integer.BYTES * 4);
    public static int CALL = 0;
    public static int DELEGATECALL = 1;
    public static int CALLCODE = 2;
    public static int CREATE = 3;

    private ExecutionHelper helper;
    private Address origin;
    private byte[] originalTxHash;

    public Address address;
    public Address sender;
    private Address blockCoinbase;
    private DataWord nrgPrice;
    private DataWord callValue;
    private DataWord blockDifficulty;
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
            DataWord nrgPrice,
            long nrgLimit,
            DataWord callValue,
            byte[] callData,
            int depth,
            int kind,
            int flags,
            Address blockCoinbase,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            DataWord blockDifficulty) {

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

        this.helper = new ExecutionHelper();
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
    public byte[] transactionHash() {
        return txHash;
    }

    /** @return the transaction address. */
    public Address address() {
        return address;
    }

    /** @return the origination address, which is the sender of original transaction. */
    public Address origin() {
        return origin;
    }

    /** @return the transaction caller. */
    public Address sender() {
        return sender;
    }

    /** @return the nrg price in current environment. */
    public DataWord nrgPrice() {
        return nrgPrice;
    }

    /** @return the nrg limit in current environment. */
    public long nrgLimit() {
        return nrgLimit;
    }

    /** @return the deposited value by instruction/trannsaction. */
    public DataWord callValue() {
        return callValue;
    }

    /** @return the call data. */
    public byte[] callData() {
        return callData;
    }

    /** @return the execution stack depth. */
    public int depth() {
        return depth;
    }

    /** @return the transaction kind. */
    public int kind() {
        return kind;
    }

    /** @return the transaction flags. */
    public int flags() {
        return flags;
    }

    /** @return the block's beneficiary. */
    public Address blockCoinbase() {
        return blockCoinbase;
    }

    /** @return the block number. */
    public long blockNumber() {
        return blockNumber;
    }

    /** @return the block timestamp. */
    public long blockTimestamp() {
        return blockTimestamp;
    }

    /** @return the block energy limit. */
    public long blockNrgLimit() {
        return blockNrgLimit;
    }

    /** @return the block difficulty. */
    public DataWord blockDifficulty() {
        return blockDifficulty;
    }

    /** @return the transaction helper. */
    public ExecutionHelper helper() {
        return helper;
    }

    /**
     * Sets the transaction address to address.
     *
     * @param destination The new address.
     */
    public void setDestination(AionAddress destination) {
        this.address = destination;
    }

    /**
     * Sets the transaction hash to txHash.
     *
     * @param txHash The new transaction hash.
     */
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
    public byte[] getOriginalTxHash() {
        return originalTxHash;
    }
}
