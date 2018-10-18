/*******************************************************************************
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
 *
 * Contributors:
 *     Aion foundation.

 ******************************************************************************/

package org.aion.mcf.types;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.crypto.ISignature;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.math.BigInteger;

public abstract class AbstractTransaction implements ITransaction {

    private static final int nrgDigits = 64;

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    /* SHA3 hash of the RLP encoded transaction */
    protected byte[] hash;

    /* the amount of ether to transfer (calculated as wei) */
    protected byte[] value;

    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    protected byte[] data;

    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    protected Address to;

    /* a counter used to make sure each transaction can only be processed once */
    protected byte[] nonce;

    /* timeStamp is a 8-bytes array shown the time of the transaction signed by the kernel, the unit is nanosecond. */
    protected byte[] timeStamp;

    protected long nrg;

    protected long nrgPrice;

    /* define transaction type. */
    protected byte type;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    protected ISignature signature;

    public AbstractTransaction() {
    }

    public AbstractTransaction(byte[] nonce, Address receiveAddress, byte[] value, byte[] data) {
        this.nonce = nonce;
        this.to = receiveAddress;
        this.value = value;
        this.data = data;
        //default type 0x01; reserve date for multi-type transaction
        this.type = 0x01;
    }

    public AbstractTransaction(byte[] nonce, Address receiveAddress, byte[] value, byte[] data, long nrg,
            long nrgPrice) {
        this(nonce, receiveAddress, value, data);
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
    }

    public AbstractTransaction(byte[] nonce, Address receiveAddress, byte[] value, byte[] data, long nrg, long nrgPrice,
            byte type) throws Exception {
        this(nonce, receiveAddress, value, data, nrg, nrgPrice);

        if (type == 0x00) {
            throw new Exception("Incorrect tx type!");
        }

        this.type = type;
    }

    public abstract byte[] getEncoded();

    public abstract Address getFrom();

    public abstract Address getTo();

    public abstract byte[] getNonce();

    public abstract byte[] getTimeStamp();

    public abstract Address getContractAddress();

    public abstract AbstractTransaction clone();

    public abstract long getNrgConsume();

    public abstract void setNrgConsume(long consume);

    public abstract byte getType();

    public abstract BigInteger getNonceBI();

    public abstract BigInteger getTimeStampBI();
}
