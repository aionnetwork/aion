package org.aion.api.server.types;

import java.math.BigInteger;
import java.util.List;

import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JSON representation of a block, with more information TODO: one big hack atm to get this out the
 * door. Refactor to make it more OOP
 *
 * @author ali
 */
public class Blk {

    public static Object AionBlockToJson(
            AionBlock block, BigInteger totalDifficulty, boolean fullTransaction) {
        if (block == null) return null;

        JSONObject obj = new JSONObject();
        obj.put("number", block.getNumber());
        obj.put("hash", StringUtils.toJsonHex(block.getHash()));
        obj.put("parentHash", StringUtils.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", StringUtils.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", StringUtils.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", StringUtils.toJsonHex(block.getStateRoot()));
        obj.put(
                "receiptsRoot",
                StringUtils.toJsonHex(
                        block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));
        obj.put("difficulty", StringUtils.toJsonHex(block.getDifficulty()));
        obj.put("totalDifficulty", StringUtils.toJsonHex(totalDifficulty));

        // TODO: this is coinbase, miner, or minerAddress?
        obj.put("miner", StringUtils.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", StringUtils.toJsonHex(block.getTimestamp()));
        obj.put("nonce", StringUtils.toJsonHex(block.getNonce()));
        obj.put("solution", StringUtils.toJsonHex(block.getHeader().getSolution()));
        obj.put("gasUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        //
        obj.put("extraData", StringUtils.toJsonHex(block.getExtraData()));
        obj.put("size", new NumericalValue(block.size()).toHexString());

        JSONArray jsonTxs = new JSONArray();
        List<AionTransaction> txs = block.getTransactionsList();
        for (int i = 0; i < txs.size(); i++) {
            AionTransaction tx = txs.get(i);
            if (fullTransaction) {
                JSONObject jsonTx = new JSONObject();
                jsonTx.put(
                        "contractAddress",
                        (tx.getContractAddress() != null)
                                ? StringUtils.toJsonHex(tx.getContractAddress().toString())
                                : null);
                jsonTx.put("hash", StringUtils.toJsonHex(tx.getTransactionHash()));
                jsonTx.put("transactionIndex", i);
                jsonTx.put("value", StringUtils.toJsonHex(tx.getValue()));
                jsonTx.put("nrg", tx.getEnergyLimit());
                jsonTx.put("nrgPrice", StringUtils.toJsonHex(tx.getEnergyPrice()));
                jsonTx.put("gas", tx.getEnergyLimit());
                jsonTx.put("gasPrice", StringUtils.toJsonHex(tx.getEnergyPrice()));
                jsonTx.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
                jsonTx.put("from", StringUtils.toJsonHex(tx.getSenderAddress().toString()));
                jsonTx.put("to", StringUtils.toJsonHex(tx.getDestinationAddress().toString()));
                jsonTx.put("timestamp", block.getTimestamp());
                jsonTx.put("input", StringUtils.toJsonHex(tx.getData()));
                jsonTx.put("blockNumber", block.getNumber());
                jsonTxs.put(jsonTx);
            } else {
                jsonTxs.put(StringUtils.toJsonHex(tx.getTransactionHash()));
            }
        }
        obj.put("transactions", jsonTxs);
        return obj;
    }

    @SuppressWarnings("Duplicates")
    public static JSONObject AionBlockOnlyToJson(AionBlock block, BigInteger totalDifficulty) {
        if (block == null) return null;

        JSONObject obj = new JSONObject();
        obj.put("number", block.getNumber());
        obj.put("hash", StringUtils.toJsonHex(block.getHash()));
        obj.put("parentHash", StringUtils.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", StringUtils.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", StringUtils.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", StringUtils.toJsonHex(block.getStateRoot()));
        obj.put(
                "receiptsRoot",
                StringUtils.toJsonHex(
                        block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));
        obj.put("difficulty", StringUtils.toJsonHex(block.getDifficulty()));
        obj.put("totalDifficulty", StringUtils.toJsonHex(totalDifficulty));

        obj.put("miner", StringUtils.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", StringUtils.toJsonHex(block.getTimestamp()));
        obj.put("nonce", StringUtils.toJsonHex(block.getNonce()));
        obj.put("solution", StringUtils.toJsonHex(block.getHeader().getSolution()));
        obj.put("gasUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));

        obj.put("extraData", StringUtils.toJsonHex(block.getExtraData()));
        obj.put("size", block.size());
        obj.put("numTransactions", block.getTransactionsList().size());

        return obj;
    }
}
