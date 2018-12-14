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

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.Log;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.vm.api.interfaces.IExecutionLog;

/** aion transaction receipt class. */
public class AionTxReceipt extends AbstractTxReceipt<AionTransaction> {

    private long energyUsed;

    public AionTxReceipt() {}

    public AionTxReceipt(byte[] rlp) {

        RLPList params = RLP.decode2(rlp);
        RLPList receipt = (RLPList) params.get(0);

        RLPItem postTxStateRLP = (RLPItem) receipt.get(0);
        RLPItem bloomRLP = (RLPItem) receipt.get(1);
        RLPList logs = (RLPList) receipt.get(2);
        RLPItem result = (RLPItem) receipt.get(3);

        postTxState = nullToEmpty(postTxStateRLP.getRLPData());
        bloomFilter = new Bloom(bloomRLP.getRLPData());
        executionResult =
                (executionResult = result.getRLPData()) == null
                        ? EMPTY_BYTE_ARRAY
                        : executionResult;
        energyUsed = ByteUtil.byteArrayToLong(receipt.get(4).getRLPData());

        if (receipt.size() > 5) {
            byte[] errBytes = receipt.get(5).getRLPData();
            error = errBytes != null ? new String(errBytes, StandardCharsets.UTF_8) : "";
        }

        for (RLPElement log : logs) {
            Log logInfo = new Log(log.getRLPData());
            logInfoList.add(logInfo);
        }

        rlpEncoded = rlp;
    }

    public AionTxReceipt(byte[] postTxState, Bloom bloomFilter, List<IExecutionLog> logInfoList) {
        this.postTxState = postTxState;
        this.bloomFilter = bloomFilter;
        this.logInfoList = logInfoList;
    }

    /**
     * Used for Receipt trie hash calculation. Should contain only the following items encoded:
     * [postTxState, bloomFilter, logInfoList]
     */
    public byte[] getReceiptTrieEncoded() {
        return getEncoded(true);
    }

    /** Used for serialization, contains all the receipt data encoded */
    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            rlpEncoded = getEncoded(false);
        }

        return rlpEncoded;
    }

    public void setNrgUsed(long l) {
        this.energyUsed = l;
    }

    public long getEnergyUsed() {
        return this.energyUsed;
    }

    public byte[] toBytes() {
        return getEncoded();
    }

    public AionTxReceipt fromBytes(byte[] bs) {
        return new AionTxReceipt(bs);
    }

    /**
     * Encodes the receipt, depending on whether the intended destination is for calculation of the
     * receipts trie, or for storage purposes. In effect the receipt stores more information than
     * what is defined in the <a href="http://http://yellowpaper.io/">YP</a>.
     *
     * @param receiptTrie true if target is "strictly" adhering to YP
     * @return {@code rlpEncoded} byte array representing the receipt
     */
    private byte[] getEncoded(boolean receiptTrie) {

        byte[] postTxStateRLP = RLP.encodeElement(this.postTxState);
        byte[] bloomRLP = RLP.encodeElement(this.bloomFilter.data);

        final byte[] logInfoListRLP;
        if (logInfoList != null) {
            byte[][] logInfoListE = new byte[logInfoList.size()][];

            int i = 0;
            for (IExecutionLog logInfo : logInfoList) {
                logInfoListE[i] = logInfo.getEncoded();
                ++i;
            }
            logInfoListRLP = RLP.encodeList(logInfoListE);
        } else {
            logInfoListRLP = RLP.encodeList();
        }

        return receiptTrie
                ? RLP.encodeList(postTxStateRLP, bloomRLP, logInfoListRLP)
                : RLP.encodeList(
                        postTxStateRLP,
                        bloomRLP,
                        logInfoListRLP,
                        RLP.encodeElement(executionResult),
                        RLP.encodeLong(energyUsed),
                        RLP.encodeElement(error.getBytes(StandardCharsets.UTF_8)));
    }

    /** TODO: check that this is valid, should null == valid? */
    public boolean isValid() {
        return this.error == null || this.error.equals("");
    }

    @Override
    public String toString() {
        return "TransactionReceipt["
                + "\n  , postTxState="
                + Hex.toHexString(postTxState)
                + "\n  , error="
                + error
                + "\n  , executionResult="
                + Hex.toHexString(executionResult)
                + "\n  , bloom="
                + bloomFilter.toString()
                + "\n  , logs="
                + logInfoList
                + "\n  , nrgUsed="
                + this.energyUsed
                + ']';
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;

        if (!(other instanceof AionTxReceipt)) return false;

        AionTxReceipt o = (AionTxReceipt) other;

        if (!Arrays.equals(this.executionResult, o.executionResult)) return false;

        if (!Arrays.equals(this.postTxState, o.postTxState)) return false;

        if (!Objects.equals(this.error, o.error)) return false;

        return Objects.equals(this.bloomFilter, o.bloomFilter);
    }
}
