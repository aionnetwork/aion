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
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.json.JSONObject;

/**
 * JSON representation of a transaction, with more information
 * TODO: one big hack atm to get this out the door. Refactor to make it more OOP
 * @author ali
 */
public class Tx {

    public static JSONObject InfoToJSON(AionTxInfo info, AionBlock b)
    {
        if (info == null) return null;

        AionTxReceipt receipt = info.getReceipt();
        if (receipt == null) return null;

        AionTransaction tx = receipt.getTransaction();

        return (AionTransactionToJSON(tx, b, info.getIndex()));
    }

    public static JSONObject AionTransactionToJSON(AionTransaction tx, AionBlock b, int index) {
        if (tx == null) return null;

        JSONObject json = new JSONObject();

        json.put("contractAddress", (tx.getContractAddress() != null)? TypeConverter.toJsonHex(tx.getContractAddress().toString()):null);
        json.put("hash", TypeConverter.toJsonHex(tx.getHash()));
        json.put("transactionIndex", index);
        json.put("value", TypeConverter.toJsonHex(tx.getValue()));
        json.put("nrg", tx.getNrg());
        json.put("nrgPrice", TypeConverter.toJsonHex(tx.getNrgPrice()));
        json.put("gas", tx.getNrg());
        json.put("gasPrice", TypeConverter.toJsonHex(tx.getNrgPrice()));
        json.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
        json.put("from", TypeConverter.toJsonHex(tx.getFrom().toString()));
        json.put("to", TypeConverter.toJsonHex(tx.getTo().toString()));
        json.put("timestamp", b.getTimestamp());
        json.put("input", TypeConverter.toJsonHex(tx.getData()));
        json.put("blockNumber", TypeConverter.toJsonHex(b.getNumber()));
        json.put("blockHash", TypeConverter.toJsonHex(b.getHash()));

        return json;
    }
}