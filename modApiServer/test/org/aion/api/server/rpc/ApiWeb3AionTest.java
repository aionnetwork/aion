package org.aion.api.server.rpc;

import static org.aion.util.string.StringUtils.StringHexToBigInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.aion.vm.api.types.Address;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;

import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiWeb3AionTest {

    private ApiWeb3Aion web3Api;
    private AionImpl impl;

    @Before
    public void setup() {
        impl = AionImpl.inst();
        web3Api = new ApiWeb3Aion(impl);
        LongLivedAvm.createAndStartLongLivedAvm();
    }

    @After
    public void tearDown() {
        LongLivedAvm.destroy();
    }

    @Test
    public void testEthSignTransaction() {
        Address addr = new Address(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Address toAddr = new Address(Keystore.create("testPwd"));

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
        Address addr = new Address(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Address toAddr = new Address(Keystore.create("testPwd"));

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
        Address addr = new Address(Keystore.create("testPwd"));

        Address toAddr = new Address(Keystore.create("testPwd"));

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
        Address addr = new Address(Keystore.create("testPwd"));

        Address toAddr = new Address(Keystore.create("testPwd"));

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
        req.put("address", Address.ZERO_ADDRESS().toString());
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getTransactionCount(req);
        assertNull(rsp.getError());

        assertEquals(
                impl.getPendingState().getNonce(Address.ZERO_ADDRESS()),
                StringHexToBigInteger(rsp.getResult().toString()));
    }

    @Test
    public void testEthGetBalancePending() {
        JSONObject req = new JSONObject();
        req.put("address", Address.ZERO_ADDRESS().toString());
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getBalance(req);
        assertNull(rsp.getError());

        assertEquals(
                impl.getPendingState().getBalance(Address.ZERO_ADDRESS()),
                StringHexToBigInteger(rsp.getResult().toString()));
    }

    @Test
    public void testEthGetBlockByNumberPending() {
        JSONObject req = new JSONObject();
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getBlockByNumber(req);
        assertNull(rsp.getError());

        long rspNum = rsp.toJson().getJSONObject("result").getLong("number");

        assertEquals(AionPendingStateImpl.inst().getBestBlock().getNumber(), rspNum);
    }

    @Test
    public void testEthGetTransactionFromBlockPending() {
        JSONObject req = new JSONObject();
        req.put("index", "0");
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getTransactionByBlockNumberAndIndex(req);
        assertEquals(JSONObject.NULL, rsp.getResult());
    }

    @Test
    public void testEth_call() {
        Address from = new Address("a000000000000000000000000000000000000000000000000000000000000001");
        Address to = new Address("a000000000000000000000000000000000000000000000000000000000000002");

        JSONObject tx = new JSONObject();
        tx.put("from", from.toString());
        tx.put("to", to.toString());

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);

        RpcMsg rsp = web3Api.eth_call(jsonArray);

        assertEquals(JSONObject.wrap("0x"), rsp.getResult());
    }
}
