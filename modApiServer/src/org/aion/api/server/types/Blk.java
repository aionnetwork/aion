package org.aion.api.server.types;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
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
        obj.put("hash", TypeConverter.toJsonHex(block.getHash()));
        obj.put("parentHash", TypeConverter.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", TypeConverter.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", TypeConverter.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", TypeConverter.toJsonHex(block.getStateRoot()));
        obj.put(
                "receiptsRoot",
                TypeConverter.toJsonHex(
                        block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));
        obj.put("difficulty", TypeConverter.toJsonHex(block.getDifficulty()));
        obj.put("totalDifficulty", TypeConverter.toJsonHex(totalDifficulty));

        // TODO: this is coinbase, miner, or minerAddress?
        obj.put("miner", TypeConverter.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", TypeConverter.toJsonHex(block.getTimestamp()));
        obj.put("nonce", TypeConverter.toJsonHex(block.getNonce()));
        obj.put("solution", TypeConverter.toJsonHex(block.getHeader().getSolution()));
        obj.put("gasUsed", TypeConverter.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", TypeConverter.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", TypeConverter.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", TypeConverter.toJsonHex(block.getHeader().getEnergyLimit()));
        //
        obj.put("extraData", TypeConverter.toJsonHex(block.getExtraData()));
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
                                ? TypeConverter.toJsonHex(tx.getContractAddress().toString())
                                : null);
                jsonTx.put("hash", TypeConverter.toJsonHex(tx.getTransactionHash()));
                jsonTx.put("transactionIndex", i);
                jsonTx.put("value", TypeConverter.toJsonHex(tx.getValue()));
                jsonTx.put("nrg", tx.getEnergyLimit());
                jsonTx.put("nrgPrice", TypeConverter.toJsonHex(tx.getEnergyPrice()));
                jsonTx.put("gas", tx.getEnergyLimit());
                jsonTx.put("gasPrice", TypeConverter.toJsonHex(tx.getEnergyPrice()));
                jsonTx.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
                jsonTx.put("from", TypeConverter.toJsonHex(tx.getSenderAddress().toString()));
                jsonTx.put("to", TypeConverter.toJsonHex(tx.getDestinationAddress().toString()));
                jsonTx.put("timestamp", block.getTimestamp());
                jsonTx.put("input", TypeConverter.toJsonHex(tx.getData()));
                jsonTx.put("blockNumber", block.getNumber());
                jsonTxs.put(jsonTx);
            } else {
                jsonTxs.put(TypeConverter.toJsonHex(tx.getTransactionHash()));
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
        obj.put("hash", TypeConverter.toJsonHex(block.getHash()));
        obj.put("parentHash", TypeConverter.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", TypeConverter.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", TypeConverter.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", TypeConverter.toJsonHex(block.getStateRoot()));
        obj.put(
                "receiptsRoot",
                TypeConverter.toJsonHex(
                        block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));
        obj.put("difficulty", TypeConverter.toJsonHex(block.getDifficulty()));
        obj.put("totalDifficulty", TypeConverter.toJsonHex(totalDifficulty));

        obj.put("miner", TypeConverter.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", TypeConverter.toJsonHex(block.getTimestamp()));
        obj.put("nonce", TypeConverter.toJsonHex(block.getNonce()));
        obj.put("solution", TypeConverter.toJsonHex(block.getHeader().getSolution()));
        obj.put("gasUsed", TypeConverter.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", TypeConverter.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", TypeConverter.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", TypeConverter.toJsonHex(block.getHeader().getEnergyLimit()));

        obj.put("extraData", TypeConverter.toJsonHex(block.getExtraData()));
        obj.put("size", block.size());
        obj.put("numTransactions", block.getTransactionsList().size());

        return obj;
    }
}
