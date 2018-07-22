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

    public static int CALL = 0;
    public static int DELEGATECALL = 1;
    public static int CALLCODE = 2;
    public static int CREATE = 3;

    private byte[] txHash;

    private Address address;
    private Address origin;
    private Address caller;

    private DataWord nrgPrice;
    private long nrgLimit; // NOTE: nrg_limit = tx_nrg_limit - tx_basic_cost
    private DataWord callValue;
    private byte[] callData;

    private int depth;
    private int kind;
    private int flags;

    private Address blockCoinbase;
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;
    private DataWord blockDifficulty;

    private ExecutionHelper helper;

    /**
     * Create a VM execution context.
     *
     * @param txHash
     * @param address
     * @param origin
     * @param caller
     * @param nrgPrice
     * @param nrgLimit
     * @param callValue
     * @param callData
     * @param depth
     * @param kind
     * @param flags
     * @param blockCoinbase
     * @param blockNumber
     * @param blockTimestamp
     * @param blockNrgLimit
     * @param blockDifficulty
     */
    public ExecutionContext(byte[] txHash, Address address, Address origin, Address caller, DataWord nrgPrice,
                            long nrgLimit, DataWord callValue, byte[] callData, int depth, int kind, int flags, Address blockCoinbase,
                            long blockNumber, long blockTimestamp, long blockNrgLimit, DataWord blockDifficulty) {
        super();
        this.address = address;
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
     * Binary encoding of the context, passed to FastVM.
     *
     * @return
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer
                .allocate(32 + 32 + 32 + 16 + 8 + 16 + 4 + callData.length + 4 + 4 + 4 + 32 + 8 + 8 + 8 + 16);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put(address.toBytes());
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

    // =============================
    // Transaction context
    // =============================

    public byte[] transactionHash() {
        return txHash;
    }

    /**
     * Returns the address of executing account.
     *
     * @return
     */
    public Address address() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    /**
     * Returns the origination address, which is the sender of original
     * transaction.
     *
     * @return
     */
    public Address origin() {
        return origin;
    }

    /**
     * Returns the caller address.
     *
     * @return
     */
    public Address caller() {
        return caller;
    }

    /**
     * Returns the nrg price in current environment.
     *
     * @return
     */
    public DataWord nrgPrice() {
        return nrgPrice;
    }

    /**
     * Returns the nrg limit in current environment.
     *
     * @return
     */
    public long nrgLimit() {
        return nrgLimit;
    }

    /**
     * Returns the deposited value by instruction/transaction.
     *
     * @return
     */
    public DataWord callValue() {
        return callValue;
    }

    /**
     * Returns the call data.
     *
     * @return
     */
    public byte[] callData() {
        return callData;
    }

    /**
     * Returns the execution depth.
     *
     * @return
     */
    public int depth() {
        return depth;
    }

    /**
     * Returns the call kind.
     *
     * @return
     */
    public int kind() {
        return kind;
    }

    /**
     * Returns the flags.
     *
     * @return
     */
    public int flags() {
        return flags;
    }

    // =============================
    // Block context
    // =============================

    /**
     * Returns the block's beneficiary address.
     *
     * @return
     */
    public Address blockCoinbase() {
        return blockCoinbase;
    }

    /**
     * Returns the block number.
     *
     * @return
     */
    public long blockNumber() {
        return blockNumber;
    }

    /**
     * Returns the block timestamp.
     *
     * @return
     */
    public long blockTimestamp() {
        return blockTimestamp;
    }

    /**
     * Returns the block nrg limit.
     *
     * @return
     */
    public long blockNrgLimit() {
        return blockNrgLimit;
    }

    /**
     * Returns the block difficulty.
     *
     * @return
     */
    public DataWord blockDifficulty() {
        return blockDifficulty;
    }

    // =============================
    // Extra info
    // =============================

    /**
     * Returns the transaction helper.
     *
     * @return
     */
    public ExecutionHelper helper() {
        return helper;
    }
}
