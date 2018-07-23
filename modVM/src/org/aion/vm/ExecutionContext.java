/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.vm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.DataWord;

/**
 * Execution context, including both transaction and block information.
 *
 * @author yulong
 */
public class ExecutionContext {
    private static final String NULL_MSG = "create ExecutionContext with null ";
    private static final String NEG_MSG = "must be non-negative.";
    public static int CALL = 0;
    public static int DELEGATECALL = 1;
    public static int CALLCODE = 2;
    public static int CREATE = 3;

    private ExecutionHelper helper;
    private Address recipient;
    private Address origin;
    private Address caller;
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
     * @param recipient The transaction recipient.
     * @param origin The sender of the original transaction.
     * @param caller The transaction caller.
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
     * @throws NullPointerException if any of the parameter objects are null.
     * @throws IllegalArgumentException if any numeric quantities are negative or txHash is not
     * length 32.
     */
    public ExecutionContext(byte[] txHash, Address recipient, Address origin, Address caller,
        DataWord nrgPrice, long nrgLimit, DataWord callValue, byte[] callData, int depth, int kind,
        int flags, Address blockCoinbase, long blockNumber, long blockTimestamp, long blockNrgLimit,
        DataWord blockDifficulty) {

        super();

        if (txHash == null) { throw new NullPointerException(NULL_MSG + " txHash."); }
        if (recipient == null) { throw new NullPointerException(NULL_MSG + " recipient."); }
        if (origin == null) { throw new NullPointerException(NULL_MSG + " origin."); }
        if (caller == null) { throw new NullPointerException(NULL_MSG + " caller."); }
        if (nrgPrice == null) { throw new NullPointerException(NULL_MSG + " nrgPrice."); }
        if (callValue == null) { throw new NullPointerException(NULL_MSG + " callValue."); }
        if (callData == null) { throw new NullPointerException(NULL_MSG + " callData."); }
        if (blockCoinbase == null) { throw new NullPointerException(NULL_MSG + " blockCoinbase."); }
        if (blockDifficulty == null) { throw new NullPointerException(NULL_MSG + " blockDifficulty."); }
        if (txHash.length != 32) { throw new IllegalArgumentException("txHash must be length 32."); }
        if (nrgLimit < 0) { throw new IllegalArgumentException("nrgLimit " + NEG_MSG); }
        if (depth < 0) { throw new IllegalArgumentException("depth " + NEG_MSG); }
        if (blockNumber < 0) { throw new IllegalArgumentException("blockNumber " + NEG_MSG); }
        if (blockTimestamp < 0) { throw new IllegalArgumentException("blockTimestamp " + NEG_MSG); }
        if (blockNrgLimit < 0) { throw new IllegalArgumentException("blockNrgLimit " + NEG_MSG); }

        this.recipient = recipient;
        this.origin = origin;
        this.caller = caller;
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
        this.helper = new ExecutionHelper();
    }

    /**
     * Returns a big-endian binary encoding of this ExecutionContext in the following format:
     *
     * |32b - recipient|32b - origin|32b - caller|16b - nrgPrice|8b - nrgLimit|16b - callValue|
     * 4b - callDataLength|?b - callData|4b - depth|4b - kind|4b - flags|32b - blockCoinbase|
     * 8b - blockNumber|8b - blockTimestamp|8b - blockNrgLimit|16b - blockDifficulty|
     *
     * where callDataLength is the length of callData.
     *
     * @return a binary encoding of this ExecutionContext.
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getEncodingLength());
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(recipient.toBytes());
        buffer.put(origin.toBytes());
        buffer.put(caller.toBytes());
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

    /**
     * @return the transaction hash.
     */
    public byte[] getTransactionHash() {
        return txHash;
    }

    /**
     * @return the transaction recipient.
     */
    public Address getRecipient() {
        return recipient;
    }

    /**
     * @return the origination recipient, which is the sender of original transaction.
     */
    public Address getOrigin() {
        return origin;
    }

    /**
     * @return the transaction caller.
     */
    public Address getCaller() {
        return caller;
    }

    /**
     * @return the nrg price in current environment.
     */
    public DataWord getNrgPrice() {
        return nrgPrice;
    }

    /**
     * @return the nrg limit in current environment.
     */
    public long getNrgLimit() {
        return nrgLimit;
    }

    /**
     * @return the deposited value by instruction/trannsaction.
     */
    public DataWord getCallValue() {
        return callValue;
    }

    /**
     * @return the call data.
     */
    public byte[] getCallData() {
        return callData;
    }

    /**
     * @return the execution stack depth.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @return the transaction kind.
     */
    public int getKind() {
        return kind;
    }

    /**
     * @return the transaction flags.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * @return the block's beneficiary.
     */
    public Address getBlockCoinbase() {
        return blockCoinbase;
    }

    /**
     * @return the block number.
     */
    public long getBlockNumber() {
        return blockNumber;
    }

    /**
     * @return the block timestamp.
     */
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    /**
     * @return the block energy limit.
     */
    public long getBlockNrgLimit() {
        return blockNrgLimit;
    }

    /**
     * @return the block difficulty.
     */
    public DataWord getBlockDifficulty() {
        return blockDifficulty;
    }

    /**
     * @return the transaction helper.
     */
    public ExecutionHelper getHelper() {
        return helper;
    }

    /**
     * Sets the transaction recipient to recipient.
     *
     * @param recipient The new recipient.
     */
    public void setRecipient(Address recipient) {
        if (recipient == null) { throw new NullPointerException("set null recipient."); }
        this.recipient = recipient;
    }

    /**
     * Returns the length of the big-endian binary encoding of this ExecutionContext.
     *
     * @return the legtn of this ExecutionContext's binary encoding.
     */
    private int getEncodingLength() {
        return (Address.ADDRESS_LEN * 4) + (DataWord.BYTES * 3) + (Long.BYTES * 4) +
            (Integer.BYTES * 4) + callData.length;
    }

}
