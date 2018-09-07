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

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.zero.impl.types.AionBlock;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.List;

/**
 * JSON representation of a block, with more information
 * TODO: one big hack atm to get this out the door. Refactor to make it more OOP
 * TODO: Abstractize this so it also works for IBlock or AbstractBlock
 * @author ali
 */
public class Blk {

    public static Object AionBlockToJson(IBlock block, BigInteger totalDifficulty, boolean fullTransaction) {
        if (block == null) return null;

        JSONObject obj = new JSONObject();
        obj.put("number", block.getNumber());
        obj.put("hash", TypeConverter.toJsonHex(block.getHash()));
        obj.put("parentHash", TypeConverter.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", TypeConverter.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", TypeConverter.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", TypeConverter.toJsonHex(block.getStateRoot()));
        obj.put("receiptsRoot",
                TypeConverter.toJsonHex(block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));

        putPowFields(obj, block, totalDifficulty);

        // TODO: this is coinbase, miner, or minerAddress?
        obj.put("miner", TypeConverter.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", TypeConverter.toJsonHex(block.getTimestamp()));

        //
        obj.put("extraData", TypeConverter.toJsonHex(block.getExtraData()));
        obj.put("size", new NumericalValue(block.size()).toHexString());

        JSONArray jsonTxs = new JSONArray();
        List<ITransaction> txs = block.getTransactionsList();
        for (int i = 0; i < txs.size(); i++) {
            ITransaction tx = txs.get(i);
            if (fullTransaction) {
                jsonTxs.put(getTransactionJsonObject(tx, block, i));
            } else {
                jsonTxs.put(TypeConverter.toJsonHex(tx.getHash()));
            }
        }
        obj.put("transactions", jsonTxs);
        return obj;
    }

    private static JSONObject getTransactionJsonObject(ITransaction tx, IBlock block, int index) {
        JSONObject jsonTx = new JSONObject();
        jsonTx.put("contractAddress", (tx.getContractAddress() != null)? TypeConverter.toJsonHex(tx.getContractAddress().toString()):null);
        jsonTx.put("hash", TypeConverter.toJsonHex(tx.getHash()));
        jsonTx.put("transactionIndex", index);
        jsonTx.put("value", TypeConverter.toJsonHex(tx.getValue()));
        jsonTx.put("nrg", tx.getNrg());
        jsonTx.put("nrgPrice", TypeConverter.toJsonHex(tx.getNrgPrice()));
        jsonTx.put("gas", tx.getNrg());
        jsonTx.put("gasPrice", TypeConverter.toJsonHex(tx.getNrgPrice()));
        jsonTx.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
        jsonTx.put("from", TypeConverter.toJsonHex(tx.getFrom().toString()));
        jsonTx.put("to", TypeConverter.toJsonHex(tx.getTo().toString()));
        jsonTx.put("timestamp", block.getTimestamp());
        jsonTx.put("input", TypeConverter.toJsonHex(tx.getData()));
        jsonTx.put("blockNumber", block.getNumber());
        return jsonTx;
    }

    private static void putPowFields(JSONObject obj, IBlock block, BigInteger totalDifficulty) {
        if (!(block instanceof AionBlock)) {
            return;
        }
        if (totalDifficulty != null)
            obj.put("totalDifficulty", TypeConverter.toJsonHex(totalDifficulty));
        AionBlock aionBlock = (AionBlock) block;
        obj.put("difficulty", TypeConverter.toJsonHex(aionBlock.getDifficulty()));
        obj.put("nonce", TypeConverter.toJsonHex(aionBlock.getNonce()));
        obj.put("solution", TypeConverter.toJsonHex(aionBlock.getHeader().getSolution()));
        obj.put("gasUsed", TypeConverter.toJsonHex(aionBlock.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", TypeConverter.toJsonHex(aionBlock.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", TypeConverter.toJsonHex(aionBlock.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", TypeConverter.toJsonHex(aionBlock.getHeader().getEnergyLimit()));
    }

    @SuppressWarnings("Duplicates")
    public static JSONObject AionBlockOnlyToJson(IBlock block, BigInteger totalDifficulty) {
        if (block == null) return null;

        JSONObject obj = new JSONObject();
        obj.put("number", block.getNumber());
        obj.put("hash", TypeConverter.toJsonHex(block.getHash()));
        obj.put("parentHash", TypeConverter.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", TypeConverter.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", TypeConverter.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", TypeConverter.toJsonHex(block.getStateRoot()));
        obj.put("receiptsRoot",
                TypeConverter.toJsonHex(block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));
        obj.put("miner", TypeConverter.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", TypeConverter.toJsonHex(block.getTimestamp()));

        putPowFields(obj, block, totalDifficulty);

        obj.put("extraData", TypeConverter.toJsonHex(block.getExtraData()));
        obj.put("size", block.size());
        obj.put("numTransactions", block.getTransactionsList().size());

        return obj;
    }

}
