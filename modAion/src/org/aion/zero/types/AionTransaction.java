/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.zero.types;

import static org.aion.base.util.ByteUtil.ZERO_BYTE_ARRAY;

import java.math.BigInteger;
import java.util.Arrays;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TimeInstant;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKey.MissingPrivateKeyException;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.DataWord;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.mcf.types.AbstractTransaction;

/**
 * 
 * Aion transaction class.
 *
 */
public class AionTransaction extends AbstractTransaction {

    public static final int RLP_TX_NONCE = 0, RLP_TX_TO = 1, RLP_TX_VALUE = 2, RLP_TX_DATA = 3, RLP_TX_TIMESTAMP = 4,
            RLP_TX_NRG = 5, RLP_TX_NRGPRICE = 6, RLP_TX_TYPE = 7, RLP_TX_SIG = 8;

    /* Tx in encoded form */
    protected byte[] rlpEncoded;

    private byte[] rlpRaw;

    protected Address from;

    /**
     * These four members doesn't include into the RLP encode data
     *
     */
    private long txIndexInBlock = 0;
    private long blockNumber = 0;
    private byte[] blockHash = null;
    private long nrgConsume = 0;

    /*
     * Indicates if this transaction has been parsed from the RLP-encoded data
     */
    protected boolean parsed = false;

    public AionTransaction(byte[] encodedData) {
        this.rlpEncoded = encodedData;
        parsed = false;
    }

    public AionTransaction(byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice) {
        super(nonce, to, value, data, nrg, nrgPrice);
        parsed = true;
    }

    public AionTransaction(byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice, byte type) {
        super(nonce, to, value, data, nrg, nrgPrice);
        this.type = type;
        parsed = true;
    }

    // constructor for create nrgEstimate transaction
    public AionTransaction(byte[] nonce, Address from, Address to, byte[] value, byte[] data, long nrg, long nrgPrice) {
        super(nonce, to, value, data, nrg, nrgPrice);
        this.from = from;
        parsed = true;
    }

    // For InternalTx constructor
    public AionTransaction(byte[] nonce, Address to, byte[] value, byte[] data) {
        super(nonce, to, value, data, 0L, 0L);
        parsed = true;
    }

    @Override
    public AionTransaction clone() {
        if (!parsed) {
            rlpParse();
        }

        AionTransaction tx2 = new AionTransaction(nonce, to, value, data, nrg, nrgPrice, type);

        tx2.signature = signature; // NOTE: reference copy
        tx2.timeStamp = timeStamp;

        return tx2;
    }

    public void rlpParse() {

        RLPList decodedTxList = RLP.decode2(rlpEncoded);
        RLPList tx = (RLPList) decodedTxList.get(0);

        this.nonce = tx.get(RLP_TX_NONCE).getRLPData();
        this.value = tx.get(RLP_TX_VALUE).getRLPData();
        this.data = tx.get(RLP_TX_DATA).getRLPData();

        if (tx.get(RLP_TX_TO).getRLPData() == null) {
            this.to = null;
        } else {
            this.to = Address.wrap(tx.get(RLP_TX_TO).getRLPData());
        }

        this.timeStamp = tx.get(RLP_TX_TIMESTAMP).getRLPData();
        this.nrg = new BigInteger(1, tx.get(RLP_TX_NRG).getRLPData()).longValue();
        this.nrgPrice = new BigInteger(1, tx.get(RLP_TX_NRGPRICE).getRLPData()).longValue();
        this.type = new BigInteger(1, tx.get(RLP_TX_TYPE).getRLPData()).byteValue();

        byte[] sigs = tx.get(RLP_TX_SIG).getRLPData();
        if (sigs != null) {
            // Singature Factory will decode the signature based on the algo
            // presetted in main() entry.
            ISignature is = SignatureFac.fromBytes(sigs);
            if (is != null) {
                this.signature = is;
            } else {
                // still ok if signature is null, but log it out.
                this.signature = null;
                LOG.error("tx -> unable to decode signature");
            }
        } else {
            LOG.error("tx -> no signature found");
        }

        this.parsed = true;
        this.hash = getHash();
    }

    public boolean isParsed() {
        return parsed;
    }

    public byte[] getHash() {
        if (hash != null) {
            return hash;
        }

        if (!parsed) {
            rlpParse();
        }
        byte[] plainMsg = this.getEncoded();
        // cache it.
        hash = HashUtil.h256(plainMsg);
        return hash;
    }

    public byte[] getRawHash() {
        if (!parsed) {
            rlpParse();
        }
        byte[] plainMsg = this.getEncodedRaw();
        return HashUtil.h256(plainMsg);
    }

    public byte[] getNonce() {
        if (!parsed) {
            rlpParse();
        }
        return nonce == null ? ZERO_BYTE_ARRAY : nonce;
    }

    public BigInteger getNonceBI() {
        return new BigInteger(1, getNonce());
    }

    public byte[] getTimeStamp() {
        if (!parsed) {
            rlpParse();
        }
        return this.timeStamp == null ? ZERO_BYTE_ARRAY : this.timeStamp;
    }

    public BigInteger getTimeStampBI() {
        return new BigInteger(1, getTimeStamp());
    }

    public long getNrg() {
        if (!parsed) {
            rlpParse();
        }
        return this.nrg;
    }

    public long getNrgPrice() {
        if (!parsed) {
            rlpParse();
        }
        return this.nrgPrice;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = BigInteger.valueOf(timeStamp).toByteArray();
        this.parsed = true;
    }

    public byte[] getValue() {
        if (!parsed) {
            rlpParse();
        }
        return value == null ? ZERO_BYTE_ARRAY : value;
    }

    @Override
    public Address getTo() {
        if (!parsed) {
            rlpParse();
        }
        return to;
    }

    public byte[] getData() {
        if (!parsed) {
            rlpParse();
        }
        return data;
    }

    public byte getType() {
        if (!parsed) {
            rlpParse();
        }
        return this.type;
    }

    public ISignature getSignature() {
        if (!parsed) {
            rlpParse();
        }
        return signature;
    }

    public Address getContractAddress() {
        if (!isContractCreation()) {
            return null;
        }

        Address from = this.getFrom();

        if (from == null) {
            return null;
        }

        try {
            return Address.wrap(HashUtil.calcNewAddr(from.toBytes(), this.getNonce()));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public boolean isContractCreation() {
        if (!parsed) {
            rlpParse();
        }
        return (this.to == null || this.to.equals(Address.EMPTY_ADDRESS()));
    }

    @Override
    public synchronized Address getFrom() {

        if (from != null) {
            return this.from;
        }

        if (!parsed) {
            rlpParse();
        }

        if (this.signature == null) {
            LOG.error("no signature!");
            return null;
        }

        try {
            from = Address.wrap(this.signature.getAddress());
            return from;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public void sign(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        this.signature = key.sign(this.getRawHash());
        this.rlpEncoded = null;
    }

    @Override
    public String toString() {
        return toString(Integer.MAX_VALUE);
    }

    public String toString(int maxDataSize) {
        if (!parsed) {

            rlpParse();
        }
        String dataS;
        if (data == null) {
            dataS = "";
        } else if (data.length < maxDataSize) {
            dataS = ByteUtil.toHexString(data);
        } else {
            dataS = ByteUtil.toHexString(Arrays.copyOfRange(data, 0, maxDataSize)) + "... (" + data.length + " bytes)";
        }
        return "TransactionData [" + "hash=" + ByteUtil.toHexString(hash) + ", nonce=" + new BigInteger(1, nonce)
                + ", receiveAddress=" + (to == null ? "" : to.toString()) + ", value=" + new BigInteger(1, value)
                + ", data=" + dataS + ", timeStamp=" + ByteUtil.byteArrayToLong(timeStamp) + ", Nrg=" + this.nrg
                + ", NrgPrice=" + this.nrgPrice + ", txType=" + this.type + ", sig="
                + ((signature == null) ? "null" : signature.toString()) + "]";
    }

    /**
     * For signatures you have to keep also RLP of the transaction without any
     * signature data
     */
    public byte[] getEncodedRaw() {

        if (!parsed) {
            rlpParse();
        }
        if (rlpRaw != null) {
            return rlpRaw;
        }

        byte[] nonce = RLP.encodeElement(this.nonce);

        byte[] to;
        if (this.to == null) {
            to = RLP.encodeElement(null);
        } else {
            to = RLP.encodeElement(this.to.toBytes());
        }

        byte[] value = RLP.encodeElement(this.value);
        byte[] data = RLP.encodeElement(this.data);
        byte[] timeStamp = RLP.encodeElement(this.timeStamp);
        byte[] nrg = RLP.encodeLong(this.nrg);
        byte[] nrgPrice = RLP.encodeLong(this.nrgPrice);
        byte[] type = RLP.encodeByte(this.type);

        rlpRaw = RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type);
        return rlpRaw;
    }

    public byte[] getEncoded() {

        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        byte[] nonce = RLP.encodeElement(this.nonce);

        byte[] to;
        if (this.to == null) {
            to = RLP.encodeElement(null);
        } else {
            to = RLP.encodeElement(this.to.toBytes());
        }

        byte[] value = RLP.encodeElement(this.value);
        byte[] data = RLP.encodeElement(this.data);
        byte[] timeStamp = RLP.encodeElement(this.timeStamp);
        byte[] nrg = RLP.encodeLong(this.nrg);
        byte[] nrgPrice = RLP.encodeLong(this.nrgPrice);
        byte[] type = RLP.encodeByte(this.type);

        byte[] sigs;

        if (signature == null) {
            LOG.error("Encoded transaction has no signature!");
            return null;
        }

        sigs = RLP.encodeElement(signature.toBytes());
        this.rlpEncoded = RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type, sigs);
        this.hash = this.getHash();

        return rlpEncoded;
    }

    @Override
    public int hashCode() {

        byte[] hash = this.getHash();
        int hashCode = 0;

        for (int i = 0; i < hash.length; ++i) {
            hashCode += hash[i] * i;
        }

        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof AionTransaction)) {
            return false;
        }
        AionTransaction tx = (AionTransaction) obj;

        return tx.hashCode() == this.hashCode();
    }

    public static AionTransaction createDefault(String to, BigInteger amount, BigInteger nonce, long nrg, long nrgPrice)
            throws Exception {
        return create(to, amount, nonce, nrg, nrgPrice);
    }

    public static AionTransaction create(String to, BigInteger amount, BigInteger nonce, long nrg, long nrgPrice)
            throws Exception {
        return new AionTransaction(nonce.toByteArray(), Address.wrap(to), amount.toByteArray(), null, nrg, nrgPrice);
    }

    @Override
    public void setEncoded(byte[] _encodedData) {
        this.rlpEncoded = _encodedData;
        parsed = false;
    }

    public DataWord nrgPrice() {
        return new DataWord(this.nrgPrice);
    }

    public long nrgLimit() {
        return new DataWord(this.nrg).longValue();
    }

    public long transactionCost(long blockNumber) {
        long nonZeroes = nonZeroBytesInData();
        long zeroes = zeroBytesInData();

        return (isContractCreation() ? Constants.NRG_TX_CREATE : 0) + Constants.NRG_TRANSACTION
                + zeroes * Constants.NRG_TX_DATA_ZERO + nonZeroes * Constants.NRG_TX_DATA_NONZERO;
    }

    public long nonZeroBytesInData() {
        int total = (data == null) ? 0 : data.length;

        return total - zeroBytesInData();
    }

    public long zeroBytesInData() {
        if (data == null) {
            return 0;
        }

        int c = 0;
        for (byte b : data) {
            c += (b == 0) ? 1 : 0;
        }
        return c;
    }

    public void setTxIndexInBlock(long idx) {
        this.txIndexInBlock = idx;
    }

    public void setBlockNumber(long blkNr) {
        this.blockNumber = blkNr;
    }

    public void setBlockHash(byte[] hash) {
        this.blockHash = hash;
    }

    public long getTxIndexInBlock() {
        return this.txIndexInBlock;
    }

    public long getBlockNumber() {
        return this.blockNumber;
    }

    public byte[] getBlockHash() {
        return this.blockHash;
    }

    public long getNrgConsume() {
        return this.nrgConsume;
    }

    @Override
    public void setNrgConsume(long consume) {
        this.nrgConsume = consume;
    }
}
