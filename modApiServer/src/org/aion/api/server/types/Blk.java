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

import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.List;

/**
 * JSON representation of a block, with more information
 * TODO: one big hack atm to get this out the door. Refactor to make it more OOP
 * @author ali
 */
public class Blk {

    public static Object AionBlockToJson(AionBlock block, BigInteger totalDifficulty, boolean fullTransaction) {
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
        obj.put("size", new NumericalValue(block.getEncoded().length).toHexString());

        JSONArray jsonTxs = new JSONArray();
        List<AionTransaction> txs = block.getTransactionsList();
        for (int i = 0; i < txs.size(); i++) {
            AionTransaction tx = txs.get(i);
            if (fullTransaction) {
                JSONObject jsonTx = new JSONObject();
                jsonTx.put("contractAddress", (tx.getContractAddress() != null)? TypeConverter.toJsonHex(tx.getContractAddress().toString()):null);
                jsonTx.put("hash", TypeConverter.toJsonHex(tx.getHash()));
                jsonTx.put("transactionIndex", i);
                jsonTx.put("value", TypeConverter.toJsonHex(tx.getValue()));
                jsonTx.put("nrg", tx.getNrg());
                jsonTx.put("nrgPrice", TypeConverter.toJsonHex(tx.getNrgPrice()));
                jsonTx.put("gas", tx.getNrg());
                jsonTx.put("gasPrice", TypeConverter.toJsonHex(tx.getNrgPrice()));
                jsonTx.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
                jsonTx.put("from", TypeConverter.toJsonHex(tx.getFrom().toString()));
                jsonTx.put("to", TypeConverter.toJsonHex(tx.getTo().toString()));
                jsonTx.put("timestamp", ByteUtil.byteArrayToLong(tx.getTimeStamp()));
                jsonTx.put("input", TypeConverter.toJsonHex(tx.getData()));
                jsonTx.put("blockNumber", block.getNumber());
                jsonTxs.put(jsonTx);
            } else {
                jsonTxs.put(TypeConverter.toJsonHex(tx.getHash()));
            }
        }
        obj.put("transactions", jsonTxs);
        return obj;
    }

}
