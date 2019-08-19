package org.aion.api.server.types;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.base.TxUtil;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.mcf.types.AionTxReceipt;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JSON representation of a transaction, with more information TODO: one big hack atm to get this
 * out the door. Refactor to make it more OOP
 *
 * @author ali
 */
public class Tx {

    public static JSONObject InfoToJSON(AionTxInfo info, Block b) {
        if (info == null) return null;

        AionTxReceipt receipt = info.getReceipt();
        if (receipt == null) return null;

        AionTransaction tx = receipt.getTransaction();

        return (AionTransactionToJSON(tx, b, info.getIndex()));
    }

    public static JSONObject AionTransactionToJSON(AionTransaction tx, Block b, int index) {
        if (tx == null) return null;

        JSONObject json = new JSONObject();

        AionAddress contractAddress = TxUtil.calculateContractAddress(tx);
        json.put(
                "contractAddress",
                (contractAddress != null)
                        ? StringUtils.toJsonHex(contractAddress.toString())
                        : null);
        json.put("hash", StringUtils.toJsonHex(tx.getTransactionHash()));
        json.put("transactionIndex", index);
        json.put("value", StringUtils.toJsonHex(tx.getValue()));
        json.put("nrg", tx.getEnergyLimit());
        json.put("nrgPrice", StringUtils.toJsonHex(tx.getEnergyPrice()));
        json.put("gas", tx.getEnergyLimit());
        json.put("gasPrice", StringUtils.toJsonHex(tx.getEnergyPrice()));
        json.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
        json.put("from", StringUtils.toJsonHex(tx.getSenderAddress().toString()));
        json.put(
                "to",
                StringUtils.toJsonHex(
                        tx.getDestinationAddress() == null
                                ? EMPTY_BYTE_ARRAY
                                : tx.getDestinationAddress().toByteArray()));
        json.put("timestamp", b.getTimestamp());
        json.put("input", StringUtils.toJsonHex(tx.getData()));
        json.put("blockNumber", StringUtils.toJsonHex(b.getNumber()));
        json.put("blockHash", StringUtils.toJsonHex(b.getHash()));

        return json;
    }

    public static JSONObject internalTxsToJSON(List<InternalTransaction> internalTransactions, byte[] txHash, boolean isCreatedWithInternalTransactions) {
        if (txHash == null) return null;

        JSONObject json = new JSONObject();

        json.put("hash", StringUtils.toJsonHex(txHash));
        if (isCreatedWithInternalTransactions) {
            JSONArray tx = new JSONArray();
            if (internalTransactions == null || internalTransactions.isEmpty()) {
                // empty when no internal transactions were created
                json.put("internal_transactions", tx);
            } else {
                for (int i = 0; i < internalTransactions.size(); i++) {
                    InternalTransaction itx = internalTransactions.get(i);
                    tx.put(internalTransactionToJSON(itx));
                }
                // populated with all the stored internal transactions
                json.put("internal_transactions", tx);
            }
        } else {
            // null when the internal transactions were not stored by the kernel
            json.put("internal_transactions", JSONObject.NULL);
        }
        return json;
    }

    public static JSONObject internalTransactionToJSON(InternalTransaction itx) {
        if (itx == null) return null;

        JSONObject json = new JSONObject();

        json.put("kind", itx.isCreate ? "CREATE" : "CALL");
        json.put("from", itx.sender.toString());
        if (itx.isCreate) {
            AionAddress contractAddress = TxUtil.calculateContractAddress(itx);
            json.put("contractAddress", contractAddress.toString());
        } else {
            json.put("to", itx.destination.toString());
        }
        json.put("nonce", itx.senderNonce);
        json.put("value", itx.value);
        json.put("data", StringUtils.toJsonHex(itx.copyOfData()));
        json.put("nrgLimit", itx.energyLimit);
        json.put("nrgPrice", itx.energyPrice);
        json.put("rejected", String.valueOf(itx.isRejected));

        return json;
    }
}
