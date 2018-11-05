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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.undertow.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiWeb3AionTest {
    private static final String BASE_PATH = System.getProperty("user.dir");
    private static final File GENESIS = new File(BASE_PATH + "/test_resources/genesis.json");
    private static final File CONFIG = new File(BASE_PATH + "/test_resources/config.xml");
    private static final File KEYSTORE = new File(BASE_PATH + "/keystore");
    private static final File DATABASE = new File(BASE_PATH + "/testDatabase");
    private long testStartTime;
    private ApiWeb3Aion web3Api;
    private AionImpl impl;

    @Before
    public void setUp() {
        CfgAion.inst().setReadConfigFiles(CONFIG, GENESIS);
        CfgAion.inst().setDatabaseDir(DATABASE);
        impl = AionImpl.inst();
        web3Api = new ApiWeb3Aion(impl);
        testStartTime = System.currentTimeMillis();
    }

    @After
    public void tearDown() {
        if (KEYSTORE == null) return;
        File[] AllFilesInDirectory = KEYSTORE.listFiles();
        if (AllFilesInDirectory == null) return;
        for (File file : AllFilesInDirectory) {
            if (file.lastModified() >= testStartTime) file.delete();
        }

        if (DATABASE == null) return;
        try {
            FileUtils.deleteRecursive(DATABASE.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNetPeerCount() {
        RpcMsg rpcMsg = web3Api.net_peerCount();
        assertEquals(impl.getAionHub().getP2pMgr().getActiveNodes().size(), rpcMsg.getResult());
    }

    @Test
    public void testNetVersion() {
        RpcMsg rpcMsg = web3Api.net_version();
        assertEquals(impl.getAionHub().getP2pMgr().chainId() + "", rpcMsg.getResult());
    }

    @Test
    public void testNetListening() {
        RpcMsg rpcMsg = web3Api.net_listening();
        assertEquals(true, rpcMsg.getResult());
    }

    @Test
    public void testEthProtocalVersion() {
        RpcMsg rpcMsg = web3Api.eth_protocolVersion();
        assertNotNull(rpcMsg.getResult());
    }

    @Test
    public void testEthSyncing() {
        RpcMsg rpcMsg = web3Api.eth_syncing();
        JSONObject result = (JSONObject) rpcMsg.getResult();
        if (!impl.isSyncComplete()) {
            assertEquals(
                    (long) impl.getInitialStartingBlockNumber().orElse(0L),
                    TypeConverter.StringHexToBigInteger(result.get("startingBlock").toString())
                            .longValue());
            assertEquals(
                    (long) impl.getLocalBestBlockNumber().orElse(0L),
                    TypeConverter.StringHexToBigInteger(result.get("currentBlock").toString())
                            .longValue());
            assertEquals(
                    (long) impl.getNetworkBestBlockNumber().orElse(0L),
                    TypeConverter.StringHexToBigInteger(result.get("highestBlock").toString())
                            .longValue());
        } else {
            assertEquals(false, rpcMsg.getResult());
        }
    }

    @Test
    public void testEthCoinbase() {
        RpcMsg rpcMsg = web3Api.eth_coinbase();
        assertNotNull(rpcMsg.getResult());
    }

    @Test
    public void testEthMining() {
        RpcMsg rpcMsg = web3Api.eth_mining();
        assertEquals(impl.getBlockMiner().isMining(), rpcMsg.getResult());
    }

    @Test
    public void testEthHashrate() {
        RpcMsg rpcMsg = web3Api.eth_hashrate();
        double hashrate_old = Double.parseDouble((String) rpcMsg.getResult());
        assertEquals(impl.getBlockMiner().getHashrate(), hashrate_old, 0.001);

        double hashRate = 1000;
        JSONObject params = new JSONObject();
        params.put("hashrate", hashRate);
        params.put("clientId", "");
        rpcMsg = web3Api.eth_submitHashrate(params);
        assertEquals(true, rpcMsg.getResult());

        rpcMsg = web3Api.eth_hashrate();
        double hashrate_new = Double.parseDouble((String) rpcMsg.getResult());
        assertEquals(hashrate_old + hashRate, hashrate_new, 0.001);
    }

    @Test
    public void testEthSubmitHashrate() {
        JSONObject params = new JSONObject();
        params.put("hashrate", 1000);
        params.put("clientId", "");
        RpcMsg rpcMsg = web3Api.eth_submitHashrate(params);

        assertEquals(true, rpcMsg.getResult());
    }

    @Test
    public void testEthGasPrice() {
        RpcMsg rpcMsg = web3Api.eth_gasPrice();
        String defaultPrice = Long.toHexString(10_000_000_000L);
        assertEquals("0x" + defaultPrice, rpcMsg.getResult());
    }

    @Test
    public void testEthAccounts() {
        RpcMsg rpcMsg = web3Api.eth_accounts();
        JSONArray accounts_old = (JSONArray) rpcMsg.getResult();

        Address addr = new Address(Keystore.create("testPwd"));

        rpcMsg = web3Api.eth_accounts();
        JSONArray accounts_new = (JSONArray) rpcMsg.getResult();
        assertTrue(accounts_new.similar(accounts_old.put("0x" + addr)));
    }

    @Test
    public void testEthBlockNumber() {
        RpcMsg rpcMsg = web3Api.eth_blockNumber();
        AionBlock blk = impl.getAionHub().getBlockchain().getBestBlock();
        assertEquals(blk.getNumber(), rpcMsg.getResult());
    }

    @Test
    public void testEthGetBalanceInvalidBn() {
        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.getBestBlock();

        JSONArray params = new JSONArray();
        params.put(Address.ZERO_ADDRESS());
        params.put(blk.getNumber() + 1);

        RpcMsg rpcMsg = web3Api.eth_getBalance(params);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.EXECUTION_ERROR, rpcMsg.getError());
    }

    @Test
    public void testEthGetBalance() {
        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.getBestBlock();

        JSONObject params = new JSONObject();
        params.put("address", Address.ZERO_ADDRESS());
        params.put("block", blk.getNumber());

        RpcMsg rpcMsg = web3Api.eth_getBalance(params);
        assertEquals(
                AionRepositoryImpl.inst().getBalance(Address.ZERO_ADDRESS()),
                TypeConverter.StringHexToBigInteger(rpcMsg.getResult().toString()));
    }

    @Test
    public void testEthGetTransactionCountInvalidBn() {
        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.getBestBlock();

        JSONArray params = new JSONArray();
        params.put(Address.ZERO_ADDRESS());
        params.put(blk.getNumber() + 1);

        RpcMsg rpcMsg = web3Api.eth_getTransactionCount(params);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.EXECUTION_ERROR, rpcMsg.getError());
    }

    @Test
    public void testEthGetTransactionCount() {
        IAionBlockchain bc = impl.getAionHub().getBlockchain();

        JSONObject params = new JSONObject();
        params.put("address", Address.ZERO_ADDRESS());
        params.put("block", bc.getBestBlock().getNumber());
        RpcMsg rpcMsg = web3Api.eth_getTransactionCount(params);
        BigInteger txCount_old =
                TypeConverter.StringHexToBigInteger((rpcMsg.getResult().toString()));

        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), List.of(tx), false);
        bc.add(blk);

        params.put("block", blk.getNumber());
        rpcMsg = web3Api.eth_getTransactionCount(params);
        BigInteger txCount_new = TypeConverter.StringHexToBigInteger(rpcMsg.getResult().toString());

        assertEquals(txCount_old.add(BigInteger.ONE), txCount_new);
    }

    @Test
    public void testEthGetBlockTransactionCountByHashNullHash() {
        JSONObject params = new JSONObject();
        params.put("hash", "");

        RpcMsg rpcMsg = web3Api.eth_getBlockTransactionCountByHash(params);

        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.EXECUTION_ERROR, rpcMsg.getError());
    }

    @Test
    public void testEthGetBlockTransactionCountByHash() {
        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), List.of(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONArray params = new JSONArray();
        params.put(ByteUtil.toHexString(blk.getHash()));
        RpcMsg rpcMsg = web3Api.eth_getBlockTransactionCountByHash(params);

        assertNotNull(rpcMsg.getResult());
        assertEquals("0x1", rpcMsg.getResult());
    }

    @Test
    public void testEthGetBlockTransactionCountByNumberNullBn() {
        JSONArray params = new JSONArray();
        params.put("");

        RpcMsg rpcMsg = web3Api.eth_getBlockTransactionCountByNumber(params);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthGetBlockTransactionCountByNumberPendingTx() {
        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.getBestBlock();
        assertNotNull(blk);

        JSONObject params = new JSONObject();
        params.put("block", "pending");
        RpcMsg rpcMsg = web3Api.eth_getBlockTransactionCountByNumber(params);
        assertNotNull(rpcMsg.getResult());
    }

    @Test
    public void testEthGetBlockTransactionCountByNumber() {
        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), List.of(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONObject params = new JSONObject();
        params.put("block", blk.getNumber());
        RpcMsg rpcMsg = web3Api.eth_getBlockTransactionCountByNumber(params);

        assertNotNull(rpcMsg.getResult());
        assertEquals("0x1", rpcMsg.getResult());
    }

    @Test
    public void testEthGetCodeNullRepo() {
        JSONObject params = new JSONObject();
        params.put("address", Address.ZERO_ADDRESS());
        params.put("block", "");
        RpcMsg rpcMsg = web3Api.eth_getCode(params);

        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.EXECUTION_ERROR, rpcMsg.getError());
    }

    @Test
    public void testEthGetCode() {
        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), Collections.singletonList(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONArray params = new JSONArray();
        params.put(Address.ZERO_ADDRESS()); // #TODO: is this addr or zero addr ?
        params.put(blk.getNumber());
        RpcMsg rpcMsg = web3Api.eth_getCode(params);

        assertNotNull(rpcMsg.getResult());
    }

    @Test
    public void testEthSignNullParams() {
        RpcMsg rpcMsg = web3Api.eth_sign(null);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthSignAccountNotUnlocked() {
        Address addr = new Address(Keystore.create("testPwd"));

        JSONArray params = new JSONArray();
        params.put(addr);
        params.put("test message");

        RpcMsg rpcMsg = web3Api.eth_sign(params);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.NOT_ALLOWED, rpcMsg.getError());
    }

    @Test
    public void testEthSign() {
        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        JSONObject params = new JSONObject();
        params.put("address", addr);
        params.put("message", "test message");

        RpcMsg rpcMsg = web3Api.eth_sign(params);
        assertNotNull(rpcMsg.getResult());
    }

    @Test
    public void testEthSignTransactionNullParams() {
        RpcMsg rpcMsg = web3Api.eth_signTransaction(null);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthSignTransactionMissingAddress() {
        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        JSONObject tx = new JSONObject();
        tx.put("from", "0x" + addr.toString());
        tx.put("to", "0x" + Address.ZERO_ADDRESS());
        tx.put("nonce", BigInteger.ONE);
        tx.put("value", BigInteger.ONE);
        tx.put("gas", "21000");
        tx.put("gasPrice", "2000000000");

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
        // don't unlock account

        JSONObject tx = new JSONObject();
        tx.put("from", "0x" + addr.toString());
        tx.put("to", "0x" + Address.ZERO_ADDRESS());
        tx.put("nonce", BigInteger.ONE);
        tx.put("value", BigInteger.ONE);
        tx.put("gas", "21000");
        tx.put("gasPrice", "2000000000");

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
    public void testEthSignTransaction() {
        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        JSONObject tx = new JSONObject();
        tx.put("from", "0x" + addr.toString());
        tx.put("to", "0x" + Address.ZERO_ADDRESS());
        tx.put("nonce", BigInteger.ONE);
        tx.put("value", BigInteger.ONE);
        tx.put("gas", "21000");
        tx.put("gasPrice", "2000000000");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("transaction", tx);
        jsonObject.put("address", addr);

        RpcMsg rpcMsg = web3Api.eth_signTransaction(jsonObject);
        assertNotNull(rpcMsg);

        JSONObject result = (JSONObject) rpcMsg.getResult();
        JSONObject outTx = (JSONObject) result.get("tx");
        String raw = (String) result.get("raw");

        assertNotNull(result);
        assertNotNull(raw);
        assertNotNull(tx);

        assertEquals(tx.get("to"), outTx.get("to"));
        assertEquals(
                tx.get("gasPrice").toString(),
                TypeConverter.StringHexToBigInteger(outTx.get("gasPrice").toString()).toString());
        assertEquals(
                tx.get("gasPrice").toString(),
                TypeConverter.StringHexToBigInteger(outTx.get("nrgPrice").toString()).toString());
        assertEquals(
                tx.get("gas").toString(),
                TypeConverter.StringHexToBigInteger(outTx.get("gas").toString()).toString());
        assertEquals(
                tx.get("gas").toString(),
                TypeConverter.StringHexToBigInteger(outTx.get("nrg").toString()).toString());
        assertEquals(
                tx.get("value").toString(),
                TypeConverter.StringHexToBigInteger(outTx.get("value").toString()).toString());
        assertEquals("0x", outTx.get("input").toString());
    }

    @Test
    public void testEthSendTransactionNullParams() {
        RpcMsg rpcMsg = web3Api.eth_sendTransaction(null);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthSendTransactionEmptyFromAddr() {
        JSONObject tx = new JSONObject();
        tx.put("from", "");
        tx.put("to", "0x" + Address.ZERO_ADDRESS());
        tx.put("nonce", BigInteger.ONE);
        tx.put("value", BigInteger.ONE);
        tx.put("gas", "21000");
        tx.put("gasPrice", "2000000000");

        JSONArray params = new JSONArray();
        params.put(tx);

        RpcMsg rpcMsg = web3Api.eth_sendTransaction(params);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthSendTransactionAccountNotUnlocked() {
        Address addr = new Address(Keystore.create("testPwd"));
        // don't unlock account

        JSONObject tx = new JSONObject();
        tx.put("from", "0x" + addr.toString());
        tx.put("to", "0x" + Address.ZERO_ADDRESS());
        tx.put("nonce", BigInteger.ONE);
        tx.put("value", BigInteger.ONE);
        tx.put("gas", "21000");
        tx.put("gasPrice", "2000000000");

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(tx);

        RpcMsg rpcMsg = web3Api.eth_sendTransaction(jsonArray);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.NOT_ALLOWED, rpcMsg.getError());
    }

    @Test
    public void testEthSendRawTransactionNullParams() {
        RpcMsg rpcMsg = web3Api.eth_sendRawTransaction(null);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthSendRawTransactionNullRawTx() {
        JSONArray params = new JSONArray();
        params.put("null");

        RpcMsg rpcMsg = web3Api.eth_sendRawTransaction(params);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthGetBlockByHash() {
        AionBlock blk = impl.getBlockchain().getBestBlock();
        assertEquals(blk, web3Api.getBestBlock());
        JSONObject param = new JSONObject();
        param.put("block", ByteUtil.toHexString(blk.getHash()));
        JSONObject result = (JSONObject) web3Api.eth_getBlockByHash(param).getResult();

        assertEquals(ByteUtil.toHexStringWithPrefix(blk.getHash()), result.get("hash"));
        assertEquals(blk.getNumber(), result.get("number"));
    }

    @Test
    public void testEthGetBlockByNumber() {
        AionBlock blk = impl.getBlockchain().getBestBlock();
        assertEquals(blk, web3Api.getBestBlock());

        JSONObject param = new JSONObject();
        param.put("block", blk.getNumber());
        JSONObject result = (JSONObject) web3Api.eth_getBlockByNumber(param).getResult();

        assertEquals(blk.getNumber(), result.get("number"));
        assertEquals(ByteUtil.toHexStringWithPrefix(blk.getHash()), result.get("hash"));
    }

    @Test
    public void testEthGetTransactionByHashInvalidParams() {
        JSONObject params = new JSONObject();
        RpcMsg rpcMsg = web3Api.eth_getTransactionByHash(params);

        assertNotNull(rpcMsg);
        assertNull(rpcMsg.getResult());
        assertEquals(RpcError.INVALID_PARAMS, rpcMsg.getError());
    }

    @Test
    public void testEthGetTransactionByHash() {
        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), List.of(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONObject params = new JSONObject();
        params.put("transactionHash", ByteUtil.toHexString(tx.getHash()));
        RpcMsg rpcMsg = web3Api.eth_getTransactionByHash(params);
        JSONObject result = (JSONObject) rpcMsg.getResult();

        assertEquals(ByteUtil.toHexStringWithPrefix(tx.getHash()), result.get("hash"));
        assertEquals(ByteUtil.byteArrayToLong(tx.getNonce()), result.get("nonce"));
    }

    @Test
    public void testEthGetTransactionByBlockHashAndIndex() {
        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), Collections.singletonList(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONObject params = new JSONObject();
        params.put("blockHash", ByteUtil.toHexString(blk.getHash()));
        params.put("index", 0);
        RpcMsg rpcMsg = web3Api.eth_getTransactionByBlockHashAndIndex(params);
        JSONObject result = (JSONObject) rpcMsg.getResult();

        assertEquals(ByteUtil.toHexStringWithPrefix(tx.getHash()), result.get("hash"));
        assertEquals(ByteUtil.byteArrayToLong(tx.getNonce()), result.get("nonce"));
        assertEquals(
                ByteUtil.toHexStringWithPrefix(tx.getSignature().getAddress()), result.get("from"));
    }

    @Test
    public void testEthGetTransactionByBlockNumberAndIndex() {
        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), Collections.singletonList(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONObject params = new JSONObject();
        params.put("block", blk.getNumber());
        params.put("index", 0);
        RpcMsg rpcMsg = web3Api.eth_getTransactionByBlockNumberAndIndex(params);
        JSONObject result = (JSONObject) rpcMsg.getResult();

        assertEquals(ByteUtil.toHexStringWithPrefix(tx.getHash()), result.get("hash"));
        assertEquals(ByteUtil.byteArrayToLong(tx.getNonce()), result.get("nonce"));
        assertEquals(
                ByteUtil.toHexStringWithPrefix(tx.getSignature().getAddress()), result.get("from"));
    }

    @Test
    public void testEthGetTransactionReceipt() {
        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                        Address.ZERO_ADDRESS(),
                        Address.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        "test message".getBytes(),
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        IAionBlockchain bc = impl.getAionHub().getBlockchain();
        AionBlock blk = bc.createNewBlock(bc.getBestBlock(), List.of(tx), false);
        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        bc.add(blk);

        JSONObject params = new JSONObject();
        params.put("hash", ByteUtil.toHexString(tx.getHash()));
        RpcMsg rpcMsg = web3Api.eth_getTransactionReceipt(params);
        JSONObject result = (JSONObject) rpcMsg.getResult();

        assertEquals(ByteUtil.toHexStringWithPrefix(tx.getHash()), result.get("transactionHash"));
    }

    @Test
    public void testEthGetCompilers() {
        RpcMsg rpcMsg = web3Api.eth_getCompilers();
        assertNotNull(rpcMsg.getResult());
    }
}
