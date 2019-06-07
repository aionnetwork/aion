package org.aion.api.server.types;

import static org.aion.util.string.StringUtils.toJsonHex;

import org.aion.types.AionAddress;
import org.aion.interfaces.block.Block;
import org.aion.interfaces.block.BlockHeader;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.json.JSONArray;
import org.json.JSONObject;

/** @author chris */
@SuppressWarnings("unused")
public final class TxRecpt {

    /** rpc api */
    public String blockHash;

    public Long blockNumber;

    public String contractAddress;

    public Long cumulativeNrgUsed;

    public String from;

    public Long nrgLimit;

    public Long nrgUsed;

    public Long gasPrice;

    public TxRecptLg[] logs;

    public String logsBloom;

    public String root;

    public String to;

    public String transactionHash;

    public Integer transactionIndex;

    /** pb abi */
    public byte[] txRoot;

    public AionAddress fromAddr;

    public AionAddress toAddr;

    public long txTimeStamp;

    public String txValue;

    public long txNonce;

    public String txData;

    // indicates whether the transaction was successfully processed by the network
    public boolean successful;

    public <
                    TX extends AbstractTransaction,
                    BH extends BlockHeader,
                    TXR extends AbstractTxReceipt<TX>>
            TxRecpt(
                    Block<TX, BH> block,
                    AbstractTxInfo<TXR, TX> txInfo,
                    long cumulativeNrgUsed,
                    boolean isMainchain) {

        AbstractTxReceipt<TX> receipt = txInfo.getReceipt();
        if (block != null) {
            this.blockHash = toJsonHex(txInfo.getBlockHash());
            this.blockNumber = block.getNumber();
            this.txRoot = block.getReceiptsRoot();
            this.logs = new TxRecptLg[receipt.getLogInfoList().size()];
            for (int i = 0; i < this.logs.length; i++) {
                IExecutionLog logInfo = receipt.getLogInfoList().get(i);
                this.logs[i] =
                        new TxRecptLg(
                                logInfo,
                                block,
                                txInfo.getIndex(),
                                receipt.getTransaction(),
                                i,
                                isMainchain);
            }
        }

        this.cumulativeNrgUsed = cumulativeNrgUsed;
        this.nrgUsed = ((AionTxReceipt) receipt).getEnergyUsed();
        this.gasPrice = ((AionTxReceipt) receipt).getTransaction().getEnergyPrice();
        this.nrgLimit = ((AionTxReceipt) receipt).getTransaction().getEnergyLimit();

        if (receipt.getTransaction().getContractAddress() != null)
            this.contractAddress =
                    toJsonHex(receipt.getTransaction().getContractAddress().toString());
        this.transactionHash = toJsonHex(receipt.getTransaction().getTransactionHash());
        this.transactionIndex = txInfo.getIndex();
        this.root = ByteUtil.toHexString(this.txRoot);
        this.fromAddr = receipt.getTransaction().getSenderAddress();
        this.from =
                toJsonHex(
                        this.fromAddr == null
                                ? ByteUtil.EMPTY_BYTE_ARRAY
                                : this.fromAddr.toByteArray());
        this.toAddr = receipt.getTransaction().getDestinationAddress();
        this.to = this.toAddr == null ? null : toJsonHex(this.toAddr.toByteArray());

        this.txTimeStamp = ByteUtil.byteArrayToLong(receipt.getTransaction().getTimeStamp());
        this.txValue = StringUtils.toJsonHex(txInfo.getReceipt().getTransaction().getValue());
        this.txNonce = ByteUtil.byteArrayToLong(txInfo.getReceipt().getTransaction().getNonce());
        byte[] _txData = txInfo.getReceipt().getTransaction().getData();
        this.txData = _txData == null ? "" : toJsonHex(_txData);

        this.logsBloom = txInfo.getReceipt().getBloomFilter().toString();
        this.successful = txInfo.getReceipt().isSuccessful();
    }

    public TxRecpt(
            AionTxReceipt receipt,
            AionBlock block,
            Integer txIndex,
            Long cumulativeNrgUsed,
            boolean isMainchain) {

        AionTransaction tx = receipt.getTransaction();

        if (block != null) {
            this.blockHash = toJsonHex(block.getHash());
            this.blockNumber = block.getNumber();
            this.txRoot = block.getReceiptsRoot();
        }

        this.logs = new TxRecptLg[receipt.getLogInfoList().size()];
        for (int i = 0; i < this.logs.length; i++) {
            IExecutionLog logInfo = receipt.getLogInfoList().get(i);
            this.logs[i] =
                    new TxRecptLg(
                            logInfo, block, txIndex, receipt.getTransaction(), i, isMainchain);
        }

        this.cumulativeNrgUsed = cumulativeNrgUsed;
        this.nrgUsed = receipt.getEnergyUsed();

        this.contractAddress =
                tx.getContractAddress() != null
                        ? toJsonHex(tx.getContractAddress().toString())
                        : null;
        this.transactionHash = toJsonHex(tx.getTransactionHash());
        this.transactionIndex = txIndex;
        this.root = this.txRoot != null ? ByteUtil.toHexString(this.txRoot) : null;
        this.fromAddr = tx.getSenderAddress();
        this.from =
                toJsonHex(
                        this.fromAddr == null
                                ? ByteUtil.EMPTY_BYTE_ARRAY
                                : this.fromAddr.toByteArray());
        this.toAddr = tx.getDestinationAddress();
        this.to = this.toAddr == null ? null : toJsonHex(this.toAddr.toByteArray());

        this.txTimeStamp = ByteUtil.byteArrayToLong(tx.getTimeStamp());
        this.txValue = toJsonHex(tx.getValue());
        this.txNonce = ByteUtil.byteArrayToLong(tx.getNonce());
        this.txData = tx.getData() == null ? "" : toJsonHex(tx.getData());
        this.gasPrice = tx.getEnergyPrice();
        this.nrgLimit = tx.getEnergyLimit();

        this.logsBloom = receipt.getBloomFilter().toString();
        this.successful = receipt.isSuccessful();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();

        obj.put("transactionHash", transactionHash);
        obj.put(
                "transactionIndex",
                transactionIndex == null
                        ? JSONObject.NULL
                        : toJsonHex(transactionIndex.longValue()));
        obj.put("blockHash", blockHash == null ? JSONObject.NULL : blockHash);
        obj.put("blockNumber", blockNumber == null ? JSONObject.NULL : toJsonHex(blockNumber));

        Object cumulativeGasUsed =
                this.cumulativeNrgUsed == null
                        ? JSONObject.NULL
                        : toJsonHex(this.cumulativeNrgUsed);
        obj.put("cumulativeGasUsed", cumulativeGasUsed);
        obj.put("cumulativeNrgUsed", cumulativeGasUsed);

        obj.put("gasUsed", new NumericalValue(nrgUsed).toHexString());
        obj.put("nrgUsed", new NumericalValue(nrgUsed).toHexString());

        obj.put("gasPrice", new NumericalValue(gasPrice).toHexString());
        obj.put("nrgPrice", new NumericalValue(gasPrice).toHexString());

        obj.put("gasLimit", new NumericalValue(nrgLimit).toHexString());

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
            log.put(
                    "transactionIndex",
                    transactionIndex == null
                            ? JSONObject.NULL
                            : toJsonHex(transactionIndex.longValue()));
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
