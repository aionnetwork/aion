package org.aion.api.server.rpc;

import static org.aion.util.string.StringUtils.StringHexToBigInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.aion.api.server.AvmPathManager;
import org.aion.api.server.account.AccountManager;
import org.aion.avm.provider.schedule.AvmVersionSchedule;
import org.aion.avm.provider.types.AvmConfigurations;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.vm.common.TxNrgRule;
import org.aion.zero.impl.keystore.Keystore;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.blockchain.AionImpl;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiWeb3AionTest {

    private ApiWeb3Aion web3Api;
    private AionImpl impl;
    private AccountManager accountManager;

    @Before
    public void setup() {
        impl = AionImpl.instForTest();
        web3Api = new ApiWeb3Aion(impl);
        accountManager = AccountManager.inst();

        // Configure the avm if it has not already been configured.
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        IEnergyRules energyRules = (t, l) -> {
            if (t == TransactionType.CREATE) {
                return TxNrgRule.isValidNrgContractCreate(l);
            } else {
                return TxNrgRule.isValidNrgTx(l);
            }
        };

        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot, energyRules);
    }

    @After
    public void tearDown() {
        AvmConfigurations.clear();
        accountManager.removeAllAccounts();
    }

    @Test
    public void testEthSignTransaction() {
        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

        accountManager.unlockAccount(addr, "testPwd", 50000);

        AionAddress toAddr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

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
        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

        accountManager.unlockAccount(addr, "testPwd", 50000);

        AionAddress toAddr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

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
        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

        AionAddress toAddr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

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
        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

        AionAddress toAddr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

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
        req.put("address", AddressUtils.ZERO_ADDRESS.toString());
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getTransactionCount(req);
        assertNull(rsp.getError());

        assertEquals(
                impl.getPendingState().getNonce(AddressUtils.ZERO_ADDRESS),
                StringHexToBigInteger(rsp.getResult().toString()));
    }

    @Test
    public void testEthGetBalancePending() {
        JSONObject req = new JSONObject();
        req.put("address", AddressUtils.ZERO_ADDRESS.toString());
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getBalance(req);
        assertNull(rsp.getError());

        assertEquals(
                impl.getPendingState().getBalance(AddressUtils.ZERO_ADDRESS),
                StringHexToBigInteger(rsp.getResult().toString()));
    }

    @Test
    public void testEthGetBlockByNumberPending() {
        JSONObject req = new JSONObject();
        req.put("block", "pending");

        RpcMsg rsp = web3Api.eth_getBlockByNumber(req);
        assertNull(rsp.getError());

        long rspNum = rsp.toJson().getJSONObject("result").getLong("number");

        assertEquals(impl.getBlockchain().getBestBlock().getNumber(), rspNum);
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
        AionAddress from =
                AddressUtils.wrapAddress(
                        "a000000000000000000000000000000000000000000000000000000000000001");
        AionAddress to =
                AddressUtils.wrapAddress(
                        "a000000000000000000000000000000000000000000000000000000000000002");

        JSONObject tx = new JSONObject();
        tx.put("from", from.toString());
        tx.put("to", to.toString());

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);

        RpcMsg rsp = web3Api.eth_call(jsonArray);

        assertEquals(JSONObject.wrap("0x"), rsp.getResult());
    }
}
