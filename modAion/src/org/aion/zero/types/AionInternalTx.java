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

package org.aion.zero.types;

import static org.aion.base.util.ByteUtil.toHexString;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.mcf.vm.types.DataWord;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.vm.api.interfaces.Address;

/** aion internal transaction class. */
public class AionInternalTx extends AionTransaction {

    private byte[] parentHash;
    private int deep;
    private int index;
    private boolean rejected = false;
    private String note;

    public AionInternalTx(byte[] rawData) {
        super(rawData);
    }

    public AionInternalTx(
            byte[] parentHash,
            int deep,
            int index,
            byte[] nonce,
            Address sendAddress,
            Address receiveAddress,
            byte[] value,
            byte[] data,
            String note) {

        // @TODO: pass null to nrg and nrgprice for base class ( Transaction )
        // will be safe?
        super(nonce, receiveAddress, nullToEmpty(value), nullToEmpty(data));

        this.parentHash = parentHash;
        this.deep = deep;
        this.index = index;
        this.from = sendAddress;
        this.note = note;
        this.parsed = true;
    }

    // @TODO: check this functions used by whom
    private static byte[] getData(DataWord nrgPrice) {
        return (nrgPrice == null) ? ByteUtil.EMPTY_BYTE_ARRAY : nrgPrice.getData();
    }

    public void reject() {
        this.rejected = true;
    }

    public int getDeep() {
        if (!parsed) {
            rlpParse();
        }
        return deep;
    }

    public int getIndex() {
        if (!parsed) {
            rlpParse();
        }
        return index;
    }

    public boolean isRejected() {
        if (!parsed) {
            rlpParse();
        }
        return rejected;
    }

    public String getNote() {
        if (!parsed) {
            rlpParse();
        }
        return note;
    }

    @Override
    public Address getSenderAddress() {
        if (!parsed) {
            rlpParse();
        }
        return from;
    }

    public byte[] getParentHash() {
        if (!parsed) {
            rlpParse();
        }
        return parentHash;
    }

    @Override
    public byte[] getEncoded() {
        if (rlpEncoded == null) {

            byte[] to = (this.getDestinationAddress() == null) ? new byte[0] : this
                .getDestinationAddress().toBytes();
            byte[] nonce = getNonce();
            boolean isEmptyNonce = isEmpty(nonce) || (getLength(nonce) == 1 && nonce[0] == 0);

            this.rlpEncoded =
                    RLP.encodeList(
                            RLP.encodeElement(isEmptyNonce ? null : nonce),
                            RLP.encodeElement(this.parentHash),
                            RLP.encodeElement(this.getSenderAddress().toBytes()),
                            RLP.encodeElement(to),
                            RLP.encodeElement(getValue()),
                            RLP.encodeElement(getData()),
                            RLP.encodeString(this.note),
                            encodeInt(this.deep),
                            encodeInt(this.index),
                            encodeInt(this.rejected ? 1 : 0));
        }

        return rlpEncoded;
    }

    @Override
    public byte[] getEncodedRaw() {
        return getEncoded();
    }

    @Override
    public void rlpParse() {
        RLPList decodedTxList = RLP.decode2(rlpEncoded);
        RLPList transaction = (RLPList) decodedTxList.get(0);

        int rlpIdx = 0;
        this.nonce = transaction.get(rlpIdx++).getRLPData();
        this.parentHash = transaction.get(rlpIdx++).getRLPData();
        this.from = AionAddress.wrap(transaction.get(rlpIdx++).getRLPData());
        this.to = AionAddress.wrap(transaction.get(rlpIdx++).getRLPData());
        this.value = transaction.get(rlpIdx++).getRLPData();

        // TODO: check the order
        this.data = transaction.get(rlpIdx++).getRLPData();
        this.note = new String(transaction.get(rlpIdx++).getRLPData());
        this.deep = decodeInt(transaction.get(rlpIdx++).getRLPData());
        this.index = decodeInt(transaction.get(rlpIdx++).getRLPData());
        this.rejected = decodeInt(transaction.get(rlpIdx++).getRLPData()) == 1;

        this.parsed = true;
    }

    private static byte[] encodeInt(int value) {
        return RLP.encodeElement(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static int decodeInt(byte[] encoded) {
        return isEmpty(encoded) ? 0 : new BigInteger(encoded).intValue();
    }

    @Override
    public void sign(ECKey key) throws ECKey.MissingPrivateKeyException {
        throw new UnsupportedOperationException("Cannot sign internal transaction.");
    }

    @Override
    public String toString() {
        String to = (this.getDestinationAddress() == null) ? "" : this.getDestinationAddress().toString();
        return "TransactionData ["
                + "  parentHash="
                + toHexString(getParentHash())
                + ", hash="
                + toHexString(this.getTransactionHash())
                + ", nonce="
                + toHexString(getNonce())
                + ", fromAddress="
                + this.getSenderAddress().toString()
                + ", toAddress="
                + to
                + ", value="
                + toHexString(getValue())
                + ", data="
                + toHexString(getData())
                + ", note="
                + getNote()
                + ", deep="
                + getDeep()
                + ", index="
                + getIndex()
                + ", rejected="
                + isRejected()
                + "]";
    }
}
