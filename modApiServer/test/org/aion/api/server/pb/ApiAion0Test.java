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

package org.aion.api.server.pb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import io.undertow.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import org.aion.api.server.ApiUtil;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.equihash.EquihashMiner;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ApiAion0Test {

    private byte[] msg, socketId, hash, rsp;
    private long testStartTime;

    private ApiAion0 api;

    private static final int MSG_HASH_LEN = 8;
    private static final int RSP_HEADER_NOHASH_LEN = 3;
    private static final int REQ_HEADER_NOHASH_LEN = 4;
    private static final int RSP_HEADER_LEN = RSP_HEADER_NOHASH_LEN + MSG_HASH_LEN;

    private static final String KEYSTORE_PATH;
    private static final String DATABASE_PATH = "ApiServerTestPath";
    private static final String MAINNET_PATH;

    static {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.dir");
        }
        KEYSTORE_PATH = storageDir + "/keystore";
        MAINNET_PATH = storageDir + "/mainnet";
    }

    public ApiAion0Test() {
        msg = "test message".getBytes();
        socketId = RandomUtils.nextBytes(5);
        hash = RandomUtils.nextBytes(ApiUtil.HASH_LEN);
        System.out.println("socketId set to " + ByteUtil.toHexString(socketId));
        System.out.println("hash set to " + ByteUtil.toHexString(hash));
        rsp = null;
    }

    private byte[] stripHeader(byte[] rsp) {
        boolean hasHash = (rsp[2] == 1);
        int bodyLen = rsp.length - (hasHash ? RSP_HEADER_LEN : RSP_HEADER_NOHASH_LEN);

        if (hasHash) {
            return Arrays.copyOfRange(rsp, RSP_HEADER_LEN, RSP_HEADER_LEN + bodyLen);
        } else {
            return Arrays.copyOfRange(rsp, RSP_HEADER_NOHASH_LEN, RSP_HEADER_NOHASH_LEN + bodyLen);
        }
    }

    private byte[] sendRequest(int s, int f) {
        byte[] request =
                ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length)
                        .put(api.getApiVersion())
                        .put((byte) s)
                        .put((byte) f)
                        .put((byte) 1)
                        .put(hash)
                        .put(msg)
                        .array();

        return api.process(request, socketId);
    }

    private byte[] sendRequest(int s, int f, byte[] reqBody) {
        byte[] request =
                ByteBuffer.allocate(reqBody.length + REQ_HEADER_NOHASH_LEN + hash.length)
                        .put(api.getApiVersion())
                        .put((byte) s)
                        .put((byte) f)
                        .put((byte) 1)
                        .put(hash)
                        .put(reqBody)
                        .array();

        return api.process(request, socketId);
    }

    @Before
    public void setup() {
        CfgAion.inst().getDb().setPath(DATABASE_PATH);
        api = new ApiAion0(AionImpl.inst());
        testStartTime = System.currentTimeMillis();
    }

    @After
    public void tearDown() {
        api.shutDown();
        rsp = null;

        // get a list of all the files in keystore directory
        File folder = new File(KEYSTORE_PATH);
        try {
            FileUtils.deleteRecursive(folder.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        folder = new File(DATABASE_PATH);
        try {
            FileUtils.deleteRecursive(folder.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        folder = new File(MAINNET_PATH);
        try {
            FileUtils.deleteRecursive(folder.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testHeartBeatMsg() {
        byte[] msg =
                ByteBuffer.allocate(api.getApiHeaderLen())
                        .put(api.getApiVersion())
                        .put((byte) Message.Servs.s_hb_VALUE)
                        .array();
        assertTrue(ApiAion0.heartBeatMsg(msg));
        assertFalse(ApiAion0.heartBeatMsg(null));

        msg[0] = 0;
        assertFalse(ApiAion0.heartBeatMsg(msg));
    }

    @Test
    public void testProcessProtocolVersion() throws Exception {
        rsp = sendRequest(Message.Servs.s_net_VALUE, Message.Funcs.f_protocolVersion_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_protocolVersion pv = Message.rsp_protocolVersion.parseFrom(stripHeader(rsp));
        assertEquals(Integer.toString(api.getApiVersion()), pv.getApi());
        assertEquals(Version.KERNEL_VERSION, pv.getKernel());
        assertEquals(EquihashMiner.VERSION, pv.getMiner());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_protocolVersion_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessMinerAddress() throws Exception {
        rsp = sendRequest(Message.Servs.s_wallet_VALUE, Message.Funcs.f_minerAddress_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_minerAddress ma = Message.rsp_minerAddress.parseFrom(stripHeader(rsp));
        assertEquals(
                ByteString.copyFrom(TypeConverter.StringHexToByteArray(api.getCoinbase())),
                ma.getMinerAddr());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_minerAddress_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessAccountsValue() throws Exception {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        rsp = sendRequest(Message.Servs.s_wallet_VALUE, Message.Funcs.f_accounts_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_accounts accts = Message.rsp_accounts.parseFrom(stripHeader(rsp));
        assertEquals(api.getAccounts().size(), accts.getAccoutCount());

        assertEquals(
                ByteString.copyFrom(
                        TypeConverter.StringHexToByteArray((String) api.getAccounts().get(0))),
                accts.getAccout(0));

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_accounts_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessBlockNumber() throws Exception {
        rsp = sendRequest(Message.Servs.s_chain_VALUE, Message.Funcs.f_blockNumber_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_blockNumber rslt = Message.rsp_blockNumber.parseFrom(stripHeader(rsp));
        assertEquals(api.getBestBlock().getNumber(), rslt.getBlocknumber());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_blockNumber_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessUnlockAccount() {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_unlockAccount reqBody =
                Message.req_unlockAccount
                        .newBuilder()
                        .setAccount(ByteString.copyFrom(addr.toBytes()))
                        .setDuration(500)
                        .setPassword("testPwd")
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_wallet_VALUE,
                        Message.Funcs.f_unlockAccount_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_unlockAccount_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetBalance() throws Exception {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getBalance reqBody =
                Message.req_getBalance
                        .newBuilder()
                        .setAddress(ByteString.copyFrom(addr.toBytes()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getBalance_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBalance rslt = Message.rsp_getBalance.parseFrom(stripHeader(rsp));
        assertEquals(ByteString.copyFrom(api.getBalance(addr).toByteArray()), rslt.getBalance());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getBalance_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetNonce() throws Exception {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getNonce reqBody =
                Message.req_getNonce
                        .newBuilder()
                        .setAddress(ByteString.copyFrom(addr.toBytes()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getNonce_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getNonce rslt = Message.rsp_getNonce.parseFrom(stripHeader(rsp));
        assertEquals(ByteString.copyFrom(api.getBalance(addr).toByteArray()), rslt.getNonce());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getNonce_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetNrgPrice() throws Exception {
        rsp = sendRequest(Message.Servs.s_tx_VALUE, Message.Funcs.f_getNrgPrice_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getNrgPrice rslt = Message.rsp_getNrgPrice.parseFrom(stripHeader(rsp));
        assertNotEquals(0, rslt.getNrgPrice());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getNrgPrice_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessCompilePass() throws Exception {
        // Taken from FastVM CompilerTest.java
        String contract =
                "pragma solidity ^0.4.0;\n"
                        + //
                        "\n"
                        + //
                        "contract SimpleStorage {\n"
                        + //
                        "    uint storedData;\n"
                        + //
                        "\n"
                        + //
                        "    function set(uint x) {\n"
                        + //
                        "        storedData = x;\n"
                        + //
                        "    }\n"
                        + //
                        "\n"
                        + //
                        "    function get() constant returns (uint) {\n"
                        + //
                        "        return storedData;\n"
                        + //
                        "    }\n"
                        + //
                        "}";

        Message.req_compileSolidity reqBody =
                Message.req_compileSolidity.newBuilder().setSource(contract).build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_compile_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_compile rslt = Message.rsp_compile.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getConstractsCount());
        assertNotNull(rslt.getConstractsMap().get("SimpleStorage"));
    }

    @Test
    public void testProcessCompileFail() {
        Message.req_compileSolidity reqBody =
                Message.req_compileSolidity.newBuilder().setSource("This should fail").build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_compile_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_fail_compile_contract_VALUE, rsp[1]);

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_compile_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetCode() throws Exception {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getCode reqBody =
                Message.req_getCode
                        .newBuilder()
                        .setAddress(ByteString.copyFrom(addr.toBytes()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_getCode_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getCode rslt = Message.rsp_getCode.parseFrom(stripHeader(rsp));
        assertEquals(ByteString.copyFrom(api.getCode(addr)), rslt.getCode());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getCode_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetTR() throws Exception {
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionReceipt reqBody =
                Message.req_getTransactionReceipt
                        .newBuilder()
                        .setTxHash(ByteString.copyFrom(tx.getTransactionHash()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_getTransactionReceipt_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getTransactionReceipt rslt =
                Message.rsp_getTransactionReceipt.parseFrom(stripHeader(rsp));
        assertEquals(ByteString.copyFrom(AionAddress.ZERO_ADDRESS().toBytes()), rslt.getTo());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getTransactionReceipt_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessCall() throws Exception {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_call reqBody =
                Message.req_call
                        .newBuilder()
                        .setData(ByteString.copyFrom(msg))
                        .setFrom(ByteString.copyFrom(addr.toBytes()))
                        .setValue(ByteString.copyFrom("1234".getBytes()))
                        .setTo(ByteString.copyFrom(AionAddress.ZERO_ADDRESS().toBytes()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_call_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_call rslt = Message.rsp_call.parseFrom(stripHeader(rsp));
        assertNotNull(rslt.getResult());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_call_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetBlockByNumber() throws Exception {
        Message.req_getBlockByNumber reqBody =
                Message.req_getBlockByNumber
                        .newBuilder()
                        .setBlockNumber(api.getBestBlock().getNumber())
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getBlockByNumber_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlock rslt = Message.rsp_getBlock.parseFrom(stripHeader(rsp));
        assertEquals(api.getBestBlock().getNumber(), rslt.getBlockNumber());
        assertEquals(api.getBestBlock().getNrgConsumed(), rslt.getNrgConsumed());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlockByNumber_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetBlockByHash() throws Exception {
        Message.req_getBlockByHash reqBody =
                Message.req_getBlockByHash
                        .newBuilder()
                        .setBlockHash(ByteString.copyFrom(api.getBestBlock().getHash()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getBlockByHash_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlock rslt = Message.rsp_getBlock.parseFrom(stripHeader(rsp));
        assertEquals(api.getBestBlock().getNumber(), rslt.getBlockNumber());
        assertEquals(api.getBestBlock().getNrgConsumed(), rslt.getNrgConsumed());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlockByHash_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetTxByBlockHashAndIndex() throws Exception {
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionByBlockHashAndIndex reqBody =
                Message.req_getTransactionByBlockHashAndIndex
                        .newBuilder()
                        .setBlockHash(ByteString.copyFrom(blk.getHash()))
                        .setTxIndex(0)
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getTransactionByBlockHashAndIndex_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getTransaction rslt = Message.rsp_getTransaction.parseFrom(stripHeader(rsp));
        assertEquals(blk.getNumber(), rslt.getBlocknumber());
        assertEquals(ByteString.copyFrom(tx.getData()), rslt.getData());
        assertEquals(tx.getEnergyPrice(), rslt.getNrgPrice());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getTransactionByBlockHashAndIndex_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetTxByBlockNumberAndIndex() throws Exception {
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionByBlockNumberAndIndex reqBody =
                Message.req_getTransactionByBlockNumberAndIndex
                        .newBuilder()
                        .setBlockNumber(blk.getNumber())
                        .setTxIndex(0)
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getTransactionByBlockNumberAndIndex_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getTransaction rslt = Message.rsp_getTransaction.parseFrom(stripHeader(rsp));
        assertEquals(blk.getNumber(), rslt.getBlocknumber());
        assertEquals(ByteString.copyFrom(tx.getData()), rslt.getData());
        assertEquals(tx.getEnergyPrice(), rslt.getNrgPrice());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getTransactionByBlockNumberAndIndex_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetBlockTxCountByNumber() throws Exception {
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getBlockTransactionCountByNumber reqBody =
                Message.req_getBlockTransactionCountByNumber
                        .newBuilder()
                        .setBlockNumber(blk.getNumber())
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getBlockTransactionCountByNumber_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlockTransactionCount rslt =
                Message.rsp_getBlockTransactionCount.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getTxCount());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getBlockTransactionCountByNumber_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetBlockTxCountByHash() throws Exception {
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionCountByHash reqBody =
                Message.req_getTransactionCountByHash
                        .newBuilder()
                        .setTxHash(ByteString.copyFrom(blk.getHash()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getBlockTransactionCountByHash_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlockTransactionCount rslt =
                Message.rsp_getBlockTransactionCount.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getTxCount());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getBlockTransactionCountByHash_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetTxByHash() throws Exception {
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionByHash reqBody =
                Message.req_getTransactionByHash
                        .newBuilder()
                        .setTxHash(ByteString.copyFrom(tx.getTransactionHash()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getTransactionByHash_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getTransaction rslt = Message.rsp_getTransaction.parseFrom(stripHeader(rsp));
        assertEquals(blk.getNumber(), rslt.getBlocknumber());
        assertEquals(ByteString.copyFrom(tx.getData()), rslt.getData());
        assertEquals(tx.getEnergyPrice(), rslt.getNrgPrice());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getTransactionByHash_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    @Ignore
    public void testProcessGetTxCount() throws Exception {
        setup();

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);
        blk = api.getBlockByHash(blk.getHash());

        Message.req_getTransactionCount reqBody =
                Message.req_getTransactionCount
                        .newBuilder()
                        .setBlocknumber(blk.getNumber())
                        .setAddress(
                                ByteString.copyFrom(
                                        blk.getTransactionsList().get(0).getSenderAddress().toBytes()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_chain_VALUE,
                        Message.Funcs.f_getTransactionCount_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getTransactionCount rslt =
                Message.rsp_getTransactionCount.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getTxCount());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getTransactionCount_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetActiveNodes() throws Exception {
        rsp = sendRequest(Message.Servs.s_net_VALUE, Message.Funcs.f_getActiveNodes_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getActiveNodes rslt = Message.rsp_getActiveNodes.parseFrom(stripHeader(rsp));
        assertEquals(
                AionImpl.inst().getAionHub().getP2pMgr().getActiveNodes().size(),
                rslt.getNodeCount());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getActiveNodes_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetStaticNodes() throws Exception {
        rsp = sendRequest(Message.Servs.s_net_VALUE, Message.Funcs.f_getStaticNodes_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getStaticNodes rslt = Message.rsp_getStaticNodes.parseFrom(stripHeader(rsp));
        assertEquals(CfgAion.inst().getNodes().length, rslt.getNodeCount());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getStaticNodes_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessGetSolcVersion() throws Exception {
        rsp = sendRequest(Message.Servs.s_tx_VALUE, Message.Funcs.f_getSolcVersion_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getSolcVersion rslt = Message.rsp_getSolcVersion.parseFrom(stripHeader(rsp));
        assertEquals(api.solcVersion(), rslt.getVer());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getSolcVersion_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessIsSyncing() throws Exception {
        rsp = sendRequest(Message.Servs.s_net_VALUE, Message.Funcs.f_isSyncing_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_isSyncing rslt = Message.rsp_isSyncing.parseFrom(stripHeader(rsp));
        assertNotEquals(AionImpl.inst().isSyncComplete(), rslt.getSyncing());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_isSyncing_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessSyncInfo() throws Exception {
        setup();
        AionImpl impl = AionImpl.inst();

        rsp = sendRequest(Message.Servs.s_net_VALUE, Message.Funcs.f_syncInfo_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_syncInfo rslt = Message.rsp_syncInfo.parseFrom(stripHeader(rsp));
        assertNotEquals(impl.isSyncComplete(), rslt.getSyncing());
        assertEquals(
                (long) impl.getLocalBestBlockNumber().orElse(0L), (long) rslt.getChainBestBlock());
        assertEquals(
                (long) impl.getNetworkBestBlockNumber().orElse(0L),
                (long) rslt.getNetworkBestBlock());
        assertEquals(24, rslt.getMaxImportBlocks());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_syncInfo_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessAccountCreateAndLock() throws Exception {
        Message.req_accountCreate reqBody =
                Message.req_accountCreate
                        .newBuilder()
                        .addPassword("passwd0")
                        .addPassword("passwd1")
                        .addPassword("passwd2")
                        .setPrivateKey(true)
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_account_VALUE,
                        Message.Funcs.f_accountCreate_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_accountCreate rslt = Message.rsp_accountCreate.parseFrom(stripHeader(rsp));
        assertEquals(3, rslt.getAddressCount());
        ByteString addr = rslt.getAddress(0);
        assertTrue(
                api.unlockAccount(AionAddress.wrap(rslt.getAddress(0).toByteArray()), "passwd0", 500));
        assertTrue(
                api.unlockAccount(AionAddress.wrap(rslt.getAddress(1).toByteArray()), "passwd1", 500));
        assertTrue(
                api.unlockAccount(AionAddress.wrap(rslt.getAddress(2).toByteArray()), "passwd2", 500));

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_accountCreate_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);

        Message.req_accountlock reqBody2 =
                Message.req_accountlock
                        .newBuilder()
                        .setAccount(addr)
                        .setPassword("passwd0")
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_wallet_VALUE,
                        Message.Funcs.f_accountLock_VALUE,
                        reqBody2.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);
        assertEquals(0x01, rsp[3]);

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_accountLock_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessUserPrivilege() {
        rsp = sendRequest(Message.Servs.s_privilege_VALUE, Message.Funcs.f_userPrivilege_VALUE);

        assertEquals(Message.Retcode.r_fail_unsupport_api_VALUE, rsp[1]);

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_userPrivilege_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessMiningValue() throws Exception {
        rsp = sendRequest(Message.Servs.s_mine_VALUE, Message.Funcs.f_mining_VALUE);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_mining rslt = Message.rsp_mining.parseFrom(stripHeader(rsp));
        assertEquals(api.isMining(), rslt.getMining());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_mining_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessEstimateNrg() throws Exception {
        byte[] val = {50, 30};

        Message.req_estimateNrg reqBody =
                Message.req_estimateNrg
                        .newBuilder()
                        .setFrom(ByteString.copyFrom(AionAddress.ZERO_ADDRESS().toBytes()))
                        .setTo(ByteString.copyFrom(AionAddress.ZERO_ADDRESS().toBytes()))
                        .setNrg(1000)
                        .setNrgPrice(5000)
                        .setData(ByteString.copyFrom(msg))
                        .setValue(ByteString.copyFrom(val))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_estimateNrg_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_estimateNrg rslt = Message.rsp_estimateNrg.parseFrom(stripHeader(rsp));

        AionTransaction tx =
                new AionTransaction(
                        AionRepositoryImpl.inst().getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        val,
                        msg,
                        1000,
                        5000);
        tx.sign(new ECKeyEd25519());

        assertEquals(AionImpl.inst().estimateTxNrg(tx, api.getBestBlock()), rslt.getNrg());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_estimateNrg_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessExportAccounts() throws Exception {
        AionAddress addr1 = new AionAddress(Keystore.create("testPwd1"));
        AccountManager.inst().unlockAccount(addr1, "testPwd1", 50000);

        AionAddress addr2 = new AionAddress(Keystore.create("testPwd2"));
        AccountManager.inst().unlockAccount(addr2, "testPwd12", 50000);

        Message.t_Key tkey1 =
                Message.t_Key
                        .newBuilder()
                        .setAddress(ByteString.copyFrom(addr1.toBytes()))
                        .setPassword("testPwd1")
                        .build();

        Message.t_Key tkey2 =
                Message.t_Key
                        .newBuilder()
                        .setAddress(ByteString.copyFrom(addr2.toBytes()))
                        .setPassword("testPwd2")
                        .build();

        Message.req_exportAccounts reqBody =
                Message.req_exportAccounts.newBuilder().addKeyFile(tkey1).addKeyFile(tkey2).build();

        rsp =
                sendRequest(
                        Message.Servs.s_account_VALUE,
                        Message.Funcs.f_exportAccounts_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_exportAccounts rslt = Message.rsp_exportAccounts.parseFrom(stripHeader(rsp));
        assertEquals(2, rslt.getKeyFileCount());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_exportAccounts_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessImportAccounts() throws Exception {
        Message.t_PrivateKey tpkey1 =
                Message.t_PrivateKey
                        .newBuilder()
                        .setPassword("testPwd1")
                        .setPrivateKey("pkey1")
                        .build();

        Message.t_PrivateKey tpkey2 =
                Message.t_PrivateKey
                        .newBuilder()
                        .setPassword("testPwd2")
                        .setPrivateKey("pkey2")
                        .build();

        Message.req_importAccounts reqBody =
                Message.req_importAccounts
                        .newBuilder()
                        .addPrivateKey(0, tpkey1)
                        .addPrivateKey(1, tpkey2)
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_account_VALUE,
                        Message.Funcs.f_importAccounts_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_importAccounts rslt = Message.rsp_importAccounts.parseFrom(stripHeader(rsp));
        assertEquals(0, rslt.getInvalidKeyCount());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_importAccounts_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessEventRegister() throws Exception {
        AionAddress addr1 = new AionAddress(Keystore.create("testPwd1"));
        AccountManager.inst().unlockAccount(addr1, "testPwd1", 50000);

        AionAddress addr2 = new AionAddress(Keystore.create("testPwd2"));
        AccountManager.inst().unlockAccount(addr2, "testPwd12", 50000);

        Message.t_FilterCt fil1 =
                Message.t_FilterCt
                        .newBuilder()
                        .addAddresses(ByteString.copyFrom(addr1.toBytes()))
                        .addAddresses(ByteString.copyFrom(addr2.toBytes()))
                        .build();

        Message.req_eventRegister reqBody =
                Message.req_eventRegister.newBuilder().addEvents("event1").setFilter(fil1).build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_eventRegister_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_eventRegister rslt = Message.rsp_eventRegister.parseFrom(stripHeader(rsp));
        assertTrue(rslt.getResult());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_eventRegister_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessEventDeregister() throws Exception {
        Message.req_eventDeregister reqBody =
                Message.req_eventDeregister.newBuilder().addEvents("event1").build();

        rsp =
                sendRequest(
                        Message.Servs.s_tx_VALUE,
                        Message.Funcs.f_eventDeregister_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_eventRegister rslt = Message.rsp_eventRegister.parseFrom(stripHeader(rsp));
        assertFalse(rslt.getResult());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_eventDeregister_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessBlockDetails() throws Exception {
        Message.req_getBlockDetailsByNumber reqBody =
                Message.req_getBlockDetailsByNumber
                        .newBuilder()
                        .addBlkNumbers(api.getBestBlock().getNumber())
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_admin_VALUE,
                        Message.Funcs.f_getBlockDetailsByNumber_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlockDetailsByNumber rslt =
                Message.rsp_getBlockDetailsByNumber.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getBlkDetailsCount());
        Message.t_BlockDetail blkdtl = rslt.getBlkDetails(0);
        assertEquals(api.getBestBlock().getNumber(), blkdtl.getBlockNumber());
        assertEquals(api.getBestBlock().getNrgConsumed(), blkdtl.getNrgConsumed());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlockDetailsByNumber_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessBlockSqlRange() throws Exception {
        long bestBlkNum = api.getBestBlock().getNumber();

        Message.req_getBlockSqlByRange reqBody =
                Message.req_getBlockSqlByRange
                        .newBuilder()
                        .setBlkNumberStart(bestBlkNum)
                        .setBlkNumberEnd(bestBlkNum + 20)
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_admin_VALUE,
                        Message.Funcs.f_getBlockSqlByRange_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlockSqlByRange rslt =
                Message.rsp_getBlockSqlByRange.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getBlkSqlCount());
        Message.t_BlockSql blksql = rslt.getBlkSql(0);
        assertEquals(api.getBestBlock().getNumber(), blksql.getBlockNumber());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlockSqlByRange_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessBlockDetailsRange() throws Exception {
        long bestBlkNum = api.getBestBlock().getNumber();

        Message.req_getBlockDetailsByRange reqBody =
                Message.req_getBlockDetailsByRange
                        .newBuilder()
                        .setBlkNumberStart(bestBlkNum)
                        .setBlkNumberEnd(bestBlkNum + 20)
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_admin_VALUE,
                        Message.Funcs.f_getBlockDetailsByRange_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlockDetailsByRange rslt =
                Message.rsp_getBlockDetailsByRange.parseFrom(stripHeader(rsp));
        assertEquals(1, rslt.getBlkDetailsCount());
        Message.t_BlockDetail blksql = rslt.getBlkDetails(0);
        assertEquals(api.getBestBlock().getNumber(), blksql.getBlockNumber());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlockDetailsByRange_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessBlockDetailsLatest() throws Exception {
        Message.req_getBlockDetailsByLatest reqBody =
                Message.req_getBlockDetailsByLatest.newBuilder().setCount(20).build();

        rsp =
                sendRequest(
                        Message.Servs.s_admin_VALUE,
                        Message.Funcs.f_getBlockDetailsByLatest_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getBlockDetailsByLatest rslt =
                Message.rsp_getBlockDetailsByLatest.parseFrom(stripHeader(rsp));

        int expectedCount = 20, bestBlkNum = (int) api.getBestBlock().getNumber();

        if (bestBlkNum < 19) expectedCount = bestBlkNum + 1;

        assertEquals(expectedCount, rslt.getBlkDetailsCount());

        Message.t_BlockDetail blksql = rslt.getBlkDetails(expectedCount - 1);
        assertEquals(bestBlkNum, blksql.getBlockNumber());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlockDetailsByLatest_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessBlocksLatest() throws Exception {
        Message.req_getBlocksByLatest reqBody =
                Message.req_getBlocksByLatest.newBuilder().setCount(20).build();

        rsp =
                sendRequest(
                        Message.Servs.s_admin_VALUE,
                        Message.Funcs.f_getBlocksByLatest_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        int expectedCount = 20, bestBlkNum = (int) api.getBestBlock().getNumber();

        if (bestBlkNum < 19) expectedCount = bestBlkNum + 1;

        Message.rsp_getBlocksByLatest rslt =
                Message.rsp_getBlocksByLatest.parseFrom(stripHeader(rsp));
        assertEquals(expectedCount, rslt.getBlksCount());
        Message.t_Block blksql = rslt.getBlks(expectedCount - 1);
        assertEquals(bestBlkNum, blksql.getBlockNumber());

        rsp = sendRequest(Message.Servs.s_hb_VALUE, Message.Funcs.f_getBlocksByLatest_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }

    @Test
    public void testProcessAccountDetails() throws Exception {
        AionAddress addr = new AionAddress(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getAccountDetailsByAddressList reqBody =
                Message.req_getAccountDetailsByAddressList
                        .newBuilder()
                        .addAddresses(ByteString.copyFrom(addr.toBytes()))
                        .addAddresses(ByteString.copyFrom(AionAddress.ZERO_ADDRESS().toBytes()))
                        .build();

        rsp =
                sendRequest(
                        Message.Servs.s_admin_VALUE,
                        Message.Funcs.f_getAccountDetailsByAddressList_VALUE,
                        reqBody.toByteArray());

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        Message.rsp_getAccountDetailsByAddressList rslt =
                Message.rsp_getAccountDetailsByAddressList.parseFrom(stripHeader(rsp));
        assertEquals(2, rslt.getAccountsCount());
        Message.t_AccountDetail acctDtl = rslt.getAccounts(0);
        assertEquals(ByteString.copyFrom(addr.toBytes()), acctDtl.getAddress());
        assertEquals(ByteString.copyFrom(api.getBalance(addr).toByteArray()), acctDtl.getBalance());

        rsp =
                sendRequest(
                        Message.Servs.s_hb_VALUE,
                        Message.Funcs.f_getAccountDetailsByAddressList_VALUE);

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, rsp[1]);
    }
}
