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

package org.aion.api.server.rpc;

import static org.aion.base.util.TypeConverter.StringHexToBigInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.aion.base.type.AionAddress;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class ApiWeb3AionTest {

    private ApiWeb3Aion web3Api;
    private AionImpl impl;

    @Before
    public void setup() {
        impl = AionImpl.inst();
        web3Api = new ApiWeb3Aion(impl);
    }

    @Test
    public void testEthSignTransaction() {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionAddress toAddr = new AionAddress(Keystore.create("testPwd"));

        JSONObject tx = new JSONObject();
        tx.put("from", "0x" + addr.toString());
        tx.put("to", "0x" + toAddr.toString());
        tx.put("gasPrice", "20000000000");
        tx.put("gas", "21000");
        tx.put("value", "500000");
        tx.put("data", "");

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);
        jsonArray.put(addr);

        RpcMsg rpcMsg = web3Api.eth_signTransaction(jsonArray);
        assertNotNull(rpcMsg);

        JSONObject result = (JSONObject) rpcMsg.getResult();
        JSONObject outTx = (JSONObject) result.get("tx");
        String raw = (String) result.get("raw");

        assertNotNull(result);
        assertNotNull(raw);
        assertNotNull(tx);

        assertEquals(tx.get("to"), outTx.get("to"));
        assertEquals(
            tx.get("value"), StringHexToBigInteger(outTx.get("value").toString()).toString());
        assertEquals(
            tx.get("gasPrice"),
            StringHexToBigInteger(outTx.get("gasPrice").toString()).toString());
        assertEquals(
            tx.get("gasPrice"),
            StringHexToBigInteger(outTx.get("nrgPrice").toString()).toString());
        assertEquals(tx.get("gas"), StringHexToBigInteger(outTx.get("gas").toString()).toString());
        assertEquals(tx.get("gas"), StringHexToBigInteger(outTx.get("nrg").toString()).toString());
        assertEquals("0x", outTx.get("input").toString());

        JSONArray rawTxArray = new JSONArray();
        rawTxArray.put(raw);
        assertNotNull(web3Api.eth_sendRawTransaction(rawTxArray));
    }

    @Test
    public void testEthSignTransactionAddressParamIsNull() {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionAddress toAddr = new AionAddress(Keystore.create("testPwd"));

        JSONObject tx = new JSONObject();
        tx.put("from", addr.toString());
        tx.put("gasPrice", "20000000000");
        tx.put("gas", "21000");
        tx.put("to", toAddr.toString());
        tx.put("value", "500000");
        tx.put("data", "");

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);
        // don't pass address

        RpcMsg rpcMsg = web3Api.eth_signTransaction(jsonArray);
        assertNotNull(rpcMsg);

        JSONObject result = (JSONObject) rpcMsg.getResult();
        assertNull(result);
        assertEquals(RpcError.INTERNAL_ERROR, rpcMsg.getError());
    }

    @Test
    public void testEthSignTransactionAccountNotUnlocked() {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AionAddress toAddr = new AionAddress(Keystore.create("testPwd"));

        JSONObject tx = new JSONObject();
        tx.put("from", addr.toString());
        tx.put("gasPrice", "20000000000");
        tx.put("gas", "21000");
        tx.put("to", toAddr.toString());
        tx.put("value", "500000");
        tx.put("data", "");

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);
        jsonArray.put(addr);

        RpcMsg rpcMsg = web3Api.eth_signTransaction(jsonArray);
        assertNotNull(rpcMsg);

        JSONObject result = (JSONObject) rpcMsg.getResult();
        assertNull(result);
        assertEquals(RpcError.INTERNAL_ERROR, rpcMsg.getError());
    }

    @Test
    public void testEthSendTransactionAccountNotUnlocked() {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AionAddress toAddr = new AionAddress(Keystore.create("testPwd"));

        JSONObject tx = new JSONObject();
        tx.put("from", addr.toString());
        tx.put("gasPrice", "20000000000");
        tx.put("gas", "21000");
        tx.put("to", toAddr.toString());
        tx.put("value", "500000");
        tx.put("data", "");

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);
        jsonArray.put(addr);

        RpcMsg rpcMsg = web3Api.eth_sendTransaction(jsonArray);
        assertNotNull(rpcMsg);

        JSONObject result = (JSONObject) rpcMsg.getResult();
        assertNull(result);
        assertEquals(RpcError.NOT_ALLOWED, rpcMsg.getError());
    }

    @Test
    public void testEthGetTransactionCountPending() {
        JSONObject req = new JSONObject();
        req.put("address", AionAddress.ZERO_ADDRESS().toString());
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getTransactionCount(req);
        assertNull(rsp.getError());

        assertEquals(
            impl.getPendingState().getNonce(AionAddress.ZERO_ADDRESS()),
            StringHexToBigInteger(rsp.getResult().toString()));
    }

    @Test
    public void testEthGetBalancePending() {
        JSONObject req = new JSONObject();
        req.put("address", AionAddress.ZERO_ADDRESS().toString());
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getBalance(req);
        assertNull(rsp.getError());

        assertEquals(
            impl.getPendingState().getBalance(AionAddress.ZERO_ADDRESS()),
            StringHexToBigInteger(rsp.getResult().toString()));
    }

    @Test
    public void testEthGetBlockByNumberPending() {
        JSONObject req = new JSONObject();
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getBlockByNumber(req);
        assertNull(rsp.getError());

        long rspNum = rsp.toJson().getJSONObject("result").getLong("number");

        assertEquals(
            AionPendingStateImpl.inst().getBestBlock().getNumber(),
            rspNum);
    }

    @Test
    public void testEthGetTransactionFromBlockPending() {
        JSONObject req = new JSONObject();
        req.put("index", "0");
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getTransactionByBlockNumberAndIndex(req);
        assertEquals(JSONObject.NULL, rsp.getResult());

    }
}
