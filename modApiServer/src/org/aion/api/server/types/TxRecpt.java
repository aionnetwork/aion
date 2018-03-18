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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import org.aion.base.type.Address;
import static org.aion.base.util.TypeConverter.toJsonHex;

import org.aion.base.type.IBlock;
import org.aion.base.type.IBlockHeader;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 
 * @author chris
 * 
 */
@SuppressWarnings("unused")
public final class TxRecpt {

    /**
     * rpc api
     */

    public String blockHash;

    public Long blockNumber;

    public String contractAddress;

    public Long cumulativeNrgUsed;

    public String from;

    public Long nrgUsed;

    public TxRecptLg[] logs;

    public String logsBloom;

    public String root;

    public String to;

    public String transactionHash;

    public Integer transactionIndex;

    /**
     * pb abi
     */
    public byte[] txRoot;

    public Address fromAddr;

    public Address toAddr;

    public long txTimeStamp;

    public String txValue;

    public long txNonce;

    public String txData;

    // indicates whether the transaction was successfully processed by the network
    public boolean successful;

    public <TX extends AbstractTransaction, BH extends IBlockHeader, TXR extends AbstractTxReceipt<TX>> TxRecpt(
            IBlock<TX, BH> block, AbstractTxInfo<TXR, TX> txInfo, long cumulativeNrgUsed, boolean isMainchain) {

        AbstractTxReceipt<TX> receipt = txInfo.getReceipt();
        if (block != null) {
            this.blockHash = toJsonHex(txInfo.getBlockHash());
            this.blockNumber = block.getNumber();
            this.txRoot = block.getReceiptsRoot();
            this.logs = new TxRecptLg[receipt.getLogInfoList().size()];
            for (int i = 0; i < this.logs.length; i++) {
                Log logInfo = receipt.getLogInfoList().get(i);
                this.logs[i] = new TxRecptLg(logInfo, block, txInfo.getIndex(), receipt.getTransaction(), i, isMainchain);
            }
        }

        this.cumulativeNrgUsed = cumulativeNrgUsed;
        this.nrgUsed = ((AionTxReceipt) receipt).getEnergyUsed();

        if (receipt.getTransaction().getContractAddress() != null)
            this.contractAddress = toJsonHex(receipt.getTransaction().getContractAddress().toString());
        this.transactionHash = toJsonHex(receipt.getTransaction().getHash());
        this.transactionIndex = txInfo.getIndex();
        this.root = ByteUtil.toHexString(this.txRoot);
        this.fromAddr = receipt.getTransaction().getFrom();
        this.from = toJsonHex(this.fromAddr == null ? ByteUtil.EMPTY_BYTE_ARRAY : this.fromAddr.toBytes());
        this.toAddr = receipt.getTransaction().getTo();
        this.to = this.toAddr == null ? null : toJsonHex(this.toAddr.toBytes());

        this.txTimeStamp = ByteUtil.byteArrayToLong(receipt.getTransaction().getTimeStamp());
        this.txValue = TypeConverter.toJsonHex(txInfo.getReceipt().getTransaction().getValue());
        this.txNonce = ByteUtil.byteArrayToLong(txInfo.getReceipt().getTransaction().getNonce());
        byte[] _txData = txInfo.getReceipt().getTransaction().getData();
        this.txData = _txData == null ? "" : toJsonHex(_txData);

        this.logsBloom = txInfo.getReceipt().getBloomFilter().toString();
        this.successful = txInfo.getReceipt().isSuccessful();
    }

    public TxRecpt(AionTxReceipt receipt, AionBlock block, Integer txIndex, Long cumulativeNrgUsed, boolean isMainchain) {

        AionTransaction tx = receipt.getTransaction();

        if (block != null) {
            this.blockHash = toJsonHex(block.getHash());
            this.blockNumber = block.getNumber();
            this.txRoot = block.getReceiptsRoot();
        }

        this.logs = new TxRecptLg[receipt.getLogInfoList().size()];
        for (int i = 0; i < this.logs.length; i++) {
            Log logInfo = receipt.getLogInfoList().get(i);
            this.logs[i] = new TxRecptLg(logInfo, block, txIndex, receipt.getTransaction(), i, isMainchain);
        }

        this.cumulativeNrgUsed = cumulativeNrgUsed;
        this.nrgUsed = receipt.getEnergyUsed();

        this.contractAddress = tx.getContractAddress() != null ? toJsonHex(tx.getContractAddress().toString()) : null;
        this.transactionHash = toJsonHex(tx.getHash());
        this.transactionIndex = txIndex;
        this.root = this.txRoot != null ? ByteUtil.toHexString(this.txRoot) : null;
        this.fromAddr = tx.getFrom();
        this.from = toJsonHex(this.fromAddr == null ? ByteUtil.EMPTY_BYTE_ARRAY : this.fromAddr.toBytes());
        this.toAddr = tx.getTo();
        this.to = this.toAddr == null ? null : toJsonHex(this.toAddr.toBytes());

        this.txTimeStamp = ByteUtil.byteArrayToLong(tx.getTimeStamp());
        this.txValue = toJsonHex(tx.getValue());
        this.txNonce = ByteUtil.byteArrayToLong(tx.getNonce());
        this.txData = tx.getData() == null ? "" : toJsonHex(tx.getData());

        this.logsBloom = receipt.getBloomFilter().toString();
        this.successful = receipt.isSuccessful();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();

        obj.put("transactionHash", transactionHash);
        obj.put("transactionIndex", transactionIndex == null ? JSONObject.NULL : toJsonHex(transactionIndex.longValue()));
        obj.put("blockHash", blockHash == null ? JSONObject.NULL : blockHash);
        obj.put("blockNumber", blockNumber == null ? JSONObject.NULL : toJsonHex(blockNumber));

        Object cumulativeGasUsed = this.cumulativeNrgUsed == null ? JSONObject.NULL : toJsonHex(this.cumulativeNrgUsed);
        obj.put("cumulativeGasUsed", cumulativeGasUsed);
        obj.put("cumulativeNrgUsed", cumulativeGasUsed);

        obj.put("gasUsed", new NumericalValue(nrgUsed).toHexString());
        obj.put("nrgUsed", new NumericalValue(nrgUsed).toHexString());

        obj.put("contractAddress", contractAddress == null ? JSONObject.NULL : contractAddress);
        obj.put("from", from);
        obj.put("to", to == null ? JSONObject.NULL : to);
        obj.put("logsBloom", logsBloom == null ? JSONObject.NULL : logsBloom);
        obj.put("root", root == null ? JSONObject.NULL : root);
        obj.put("status", successful ? "0x1" : "0x0");

        JSONArray logArray = new JSONArray();
        for (int i = 0; i < logs.length; i++) {
            JSONObject log = new JSONObject();
            log.put("address", logs[i].address);
            log.put("data", logs[i].data);
            log.put("blockNumber", blockNumber == null ? JSONObject.NULL : toJsonHex(blockNumber));
            log.put("transactionIndex", transactionIndex == null ? JSONObject.NULL : toJsonHex(transactionIndex.longValue()));
            log.put("logIndex", toJsonHex(i));

            String[] topics = logs[i].topics;
            JSONArray topicArray = new JSONArray();
            for (int j = 0; j < topics.length; j++) {
                topicArray.put(j, topics[j]);
            }
            log.put("topics", topicArray);
            logArray.put(i, log);
        }
        obj.put("logs", logArray);

        return obj;
    }
}
