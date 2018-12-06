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
package org.aion.mcf.blockchain;

import java.math.BigInteger;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteUtil;
import org.aion.vm.api.interfaces.Address;

/**
 * Abstract Pending Transaction Class.
 *
 * @param <TX>
 */
public abstract class AbstractPendingTx<TX extends ITransaction> {

    protected TX transaction;

    protected long blockNumber;

    public AbstractPendingTx(byte[] bytes) {
        parse(bytes);
    }

    public AbstractPendingTx(TX transaction) {
        this(transaction, 0);
    }

    public AbstractPendingTx(TX transaction, long blockNumber) {
        this.transaction = transaction;
        this.blockNumber = blockNumber;
    }

    protected abstract void parse(byte[] bs);

    public TX getTransaction() {
        return transaction;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public Address getSender() {
        return transaction.getSenderAddress();
    }

    public byte[] getHash() {
        return transaction.getTransactionHash();
    }

    public byte[] getBytes() {
        byte[] numberBytes = BigInteger.valueOf(blockNumber).toByteArray();
        byte[] txBytes = transaction.getEncoded();
        byte[] bytes = new byte[1 + numberBytes.length + txBytes.length];

        bytes[0] = (byte) numberBytes.length;
        System.arraycopy(numberBytes, 0, bytes, 1, numberBytes.length);
        System.arraycopy(txBytes, 0, bytes, 1 + numberBytes.length, txBytes.length);

        return bytes;
    }

    @Override
    public String toString() {
        return "PendingTransaction ["
                + "  transaction="
                + transaction
                + ", blockNumber="
                + blockNumber
                + ']';
    }

    @Override
    public int hashCode() {
        return ByteUtil.byteArrayToInt(getSender().toBytes())
                + ByteUtil.byteArrayToInt(transaction.getNonce());
    }
}
