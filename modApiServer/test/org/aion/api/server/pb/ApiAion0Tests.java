package org.aion.api.server.pb;

import com.google.protobuf.ByteString;
import org.aion.api.server.ApiUtil;
import org.aion.base.type.Address;
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
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ApiAion0Tests {

    private byte[] msg, socketId, hash;

    private static final int MSG_HASH_LEN = 8;
    private static final int RSP_HEADER_NOHASH_LEN = 3;
    private static final int REQ_HEADER_NOHASH_LEN = 4;
    private static final int RSP_HEADER_LEN = RSP_HEADER_NOHASH_LEN + MSG_HASH_LEN;

    public ApiAion0Tests() {
        msg = "test message".getBytes();
        socketId = RandomUtils.nextBytes(5);
        hash = RandomUtils.nextBytes(ApiUtil.HASH_LEN);
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

    @Test
    public void TestCreate() {
        System.out.println("run TestCreate.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);
        api.shutDown();
    }

    @Test
    public void TestHeartBeatMsg() {
        System.out.println("run TestHeartBeatMsg.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] msg = ByteBuffer.allocate(api.getApiHeaderLen()).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE).array();
        assertTrue(ApiAion0.heartBeatMsg(msg));
        assertFalse(ApiAion0.heartBeatMsg(null));

        msg[0] = 0;
        assertFalse(ApiAion0.heartBeatMsg(msg));

        api.shutDown();
    }

    @Test
    public void TestProcessProtocolVersion() {
        System.out.println("run TestProcessProtocolVersion.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_net_VALUE)
                .put((byte) Message.Funcs.f_protocolVersion_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_protocolVersion pv = Message.rsp_protocolVersion.parseFrom(stripHeader(rsp));
            assertEquals(Integer.toString(api.getApiVersion()), pv.getApi());
            assertEquals(Version.KERNEL_VERSION, pv.getKernel());
            assertEquals(EquihashMiner.VERSION, pv.getMiner());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_protocolVersion_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();

        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessMinerAddress() {
        System.out.println("run TestProcessMinerAddress.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_wallet_VALUE)
                .put((byte) Message.Funcs.f_minerAddress_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_minerAddress ma = Message.rsp_minerAddress.parseFrom(stripHeader(rsp));

            assertEquals(ByteString.copyFrom(
                    TypeConverter.StringHexToByteArray(api.getCoinbase())),
                    ma.getMinerAddr());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_minerAddress_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessContractDeploy() {
        System.out.println("run TestProcessContractDeploy.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        byte[] val = {50, 30};


        Message.req_contractDeploy reqBody = Message.req_contractDeploy.newBuilder()
                .setFrom(ByteString.copyFrom(addr.toBytes()))
                .setNrgLimit(100000)
                .setNrgPrice(5000)
                .setData(ByteString.copyFrom(msg))
                .setValue(ByteString.copyFrom(val))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_contractDeploy_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_tx_Recved_VALUE, rsp[1]);

        try {
            Message.rsp_contractDeploy rslt = Message.rsp_contractDeploy.parseFrom(stripHeader(rsp));
            assertNotNull(rslt.getContractAddress());
            assertNotNull(rslt.getTxHash());

        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_contractDeploy_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessAccountsValue() {
        System.out.println("run TestProcessAccountsValue.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_wallet_VALUE)
                .put((byte) Message.Funcs.f_accounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_accounts accts = Message.rsp_accounts.parseFrom(stripHeader(rsp));
            assertEquals(api.getAccounts().size(),
                    accts.getAccoutCount());

            assertEquals(ByteString.copyFrom(TypeConverter.StringHexToByteArray((String) api.getAccounts().get(0))),
                    accts.getAccout(0));
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_accounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessBlockNumber() {
        System.out.println("run TestProcessBlockNumber.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_blockNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_blockNumber rslt = Message.rsp_blockNumber.parseFrom(stripHeader(rsp));
            assertEquals(api.getBestBlock().getNumber(),
                    rslt.getBlocknumber());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_blockNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessUnlockAccount() {
        System.out.println("run TestProcessUnlockAccount.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_unlockAccount reqBody = Message.req_unlockAccount.newBuilder()
                .setAccount(ByteString.copyFrom(addr.toBytes()))
                .setDuration(500)
                .setPassword("testPwd")
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_wallet_VALUE)
                .put((byte) Message.Funcs.f_unlockAccount_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_unlockAccount_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetBalance() {
        System.out.println("run TestProcessGetBalance.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getBalance reqBody = Message.req_getBalance.newBuilder()
                .setAddress(ByteString.copyFrom(addr.toBytes()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getBalance_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBalance rslt = Message.rsp_getBalance.parseFrom(stripHeader(rsp));
            assertEquals(ByteString.copyFrom(api.getBalance(addr).toByteArray()),
                    rslt.getBalance());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBalance_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetNonce() {
        System.out.println("run TestProcessGetBalance.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getNonce reqBody = Message.req_getNonce.newBuilder()
                .setAddress(ByteString.copyFrom(addr.toBytes()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getNonce_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getNonce rslt = Message.rsp_getNonce.parseFrom(stripHeader(rsp));
            assertEquals(ByteString.copyFrom(api.getBalance(addr).toByteArray()),
                    rslt.getNonce());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getNonce_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetNrgPrice() {
        System.out.println("run TestProcessGetNrgPrice.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_getNrgPrice_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getNrgPrice rslt = Message.rsp_getNrgPrice.parseFrom(stripHeader(rsp));
            assertNotEquals(0, rslt.getNrgPrice());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getNrgPrice_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessCompilePass() {
        System.out.println("run TestProcessCompilePass.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        // Taken from FastVM CompilerTest.java
        String contract = "pragma solidity ^0.4.0;\n" + //
                "\n" + //
                "contract SimpleStorage {\n" + //
                "    uint storedData;\n" + //
                "\n" + //
                "    function set(uint x) {\n" + //
                "        storedData = x;\n" + //
                "    }\n" + //
                "\n" + //
                "    function get() constant returns (uint) {\n" + //
                "        return storedData;\n" + //
                "    }\n" + //
                "}";

        Message.req_compileSolidity reqBody = Message.req_compileSolidity.newBuilder()
                .setSource(contract)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_compile_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_compile rslt = Message.rsp_compile.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getConstractsCount());
            assertNotNull(rslt.getConstractsMap().get("SimpleStorage"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }
    }

    @Test
    public void TestProcessCompileFail() {
        System.out.println("run TestProcessCompileFail.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_compileSolidity reqBody = Message.req_compileSolidity.newBuilder()
                .setSource("This should fail")
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_compile_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_fail_compile_contract_VALUE, rsp[1]);

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_compile_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessSendTransaction() {
        System.out.println("run TestProcessSendTransaction.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_sendTransaction reqBody = Message.req_sendTransaction.newBuilder()
                .setFrom(ByteString.copyFrom(addr.toBytes()))
                .setTo(ByteString.copyFrom(Address.ZERO_ADDRESS().toBytes()))
                .setNrg(500)
                .setNrgPrice(5000)
                .setNonce(ByteString.copyFrom("1".getBytes()))
                .setValue(ByteString.copyFrom("1234".getBytes()))
                .setData(ByteString.copyFrom(msg))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_sendTransaction_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_tx_Recved_VALUE, rsp[1]);

        try {
            Message.rsp_sendTransaction rslt = Message.rsp_sendTransaction.parseFrom(stripHeader(rsp));
            assertNotNull(rslt.getTxHash());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_sendTransaction_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetCode() {
        System.out.println("run TestProcessGetCode.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getCode reqBody = Message.req_getCode.newBuilder()
                .setAddress(ByteString.copyFrom(addr.toBytes()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_getCode_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getCode rslt = Message.rsp_getCode.parseFrom(stripHeader(rsp));
            assertEquals(ByteString.copyFrom(api.getCode(addr)), rslt.getCode());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getCode_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetTR() {
        System.out.println("run TestProcessGetTR.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionReceipt reqBody = Message.req_getTransactionReceipt.newBuilder()
                .setTxHash(ByteString.copyFrom(tx.getHash()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_getTransactionReceipt_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getTransactionReceipt rslt = Message.rsp_getTransactionReceipt.parseFrom(stripHeader(rsp));
            assertEquals(ByteString.copyFrom(Address.ZERO_ADDRESS().toBytes()), rslt.getTo());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getTransactionReceipt_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessCall() {
        System.out.println("run TestProcessCall.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_call reqBody = Message.req_call.newBuilder()
                .setData(ByteString.copyFrom(msg))
                .setFrom(ByteString.copyFrom(addr.toBytes()))
                .setValue(ByteString.copyFrom("1234".getBytes()))
                .setTo(ByteString.copyFrom(Address.ZERO_ADDRESS().toBytes()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_call_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_call rslt = Message.rsp_call.parseFrom(stripHeader(rsp));
            assertNotNull(rslt.getResult());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_call_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetBlockByNumber() {
        System.out.println("run TestProcessGetBlockByNumber.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_getBlockByNumber reqBody = Message.req_getBlockByNumber.newBuilder()
                .setBlockNumber(api.getBestBlock().getNumber())
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getBlockByNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlock rslt = Message.rsp_getBlock.parseFrom(stripHeader(rsp));
            assertEquals(api.getBestBlock().getNumber(), rslt.getBlockNumber());
            assertEquals(api.getBestBlock().getNrgConsumed(), rslt.getNrgConsumed());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockByNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetBlockByHash() {
        System.out.println("run TestProcessGetBlockByHash.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_getBlockByHash reqBody = Message.req_getBlockByHash.newBuilder()
                .setBlockHash(ByteString.copyFrom(api.getBestBlock().getHash()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getBlockByHash_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlock rslt = Message.rsp_getBlock.parseFrom(stripHeader(rsp));
            assertEquals(api.getBestBlock().getNumber(), rslt.getBlockNumber());
            assertEquals(api.getBestBlock().getNrgConsumed(), rslt.getNrgConsumed());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockByHash_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetTxByBlockHashAndIndex() {
        System.out.println("run TestProcessGetTxByBlockHashAndIndex.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionByBlockHashAndIndex reqBody = Message.req_getTransactionByBlockHashAndIndex.newBuilder()
                .setBlockHash(ByteString.copyFrom(blk.getHash()))
                .setTxIndex(0)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getTransactionByBlockHashAndIndex_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getTransaction rslt = Message.rsp_getTransaction.parseFrom(stripHeader(rsp));
            assertEquals(blk.getNumber(), rslt.getBlocknumber());
            assertEquals(ByteString.copyFrom(tx.getData()), rslt.getData());
            assertEquals(tx.getNrgPrice(), rslt.getNrgPrice());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getTransactionByBlockHashAndIndex_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetTxByBlockNumberAndIndex() {
        System.out.println("run TestProcessGetTxByBlockNumberAndIndex.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionByBlockNumberAndIndex reqBody = Message.req_getTransactionByBlockNumberAndIndex.newBuilder()
                .setBlockNumber(blk.getNumber())
                .setTxIndex(0)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getTransactionByBlockNumberAndIndex_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getTransaction rslt = Message.rsp_getTransaction.parseFrom(stripHeader(rsp));
            assertEquals(blk.getNumber(), rslt.getBlocknumber());
            assertEquals(ByteString.copyFrom(tx.getData()), rslt.getData());
            assertEquals(tx.getNrgPrice(), rslt.getNrgPrice());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getTransactionByBlockNumberAndIndex_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetBlockTxCountByNumber() {
        System.out.println("run TestProcessGetBlockTxCountByNumber.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getBlockTransactionCountByNumber reqBody = Message.req_getBlockTransactionCountByNumber.newBuilder()
                .setBlockNumber(blk.getNumber())
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getBlockTransactionCountByNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlockTransactionCount rslt = Message.rsp_getBlockTransactionCount.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getTxCount());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockTransactionCountByNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetBlockTxCountByHash() {
        System.out.println("run TestProcessGetBlockTxCountByHash.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionCountByHash reqBody = Message.req_getTransactionCountByHash.newBuilder()
                .setTxHash(ByteString.copyFrom(blk.getHash()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getBlockTransactionCountByHash_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlockTransactionCount rslt = Message.rsp_getBlockTransactionCount.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getTxCount());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockTransactionCountByHash_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetTxByHash() {
        System.out.println("run TestProcessGetTxByHash.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_getTransactionByHash reqBody = Message.req_getTransactionByHash.newBuilder()
                .setTxHash(ByteString.copyFrom(tx.getHash()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getTransactionByHash_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getTransaction rslt = Message.rsp_getTransaction.parseFrom(stripHeader(rsp));
            assertEquals(blk.getNumber(), rslt.getBlocknumber());
            assertEquals(ByteString.copyFrom(tx.getData()), rslt.getData());
            assertEquals(tx.getNrgPrice(), rslt.getNrgPrice());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getTransactionByHash_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetTxCount() {
        System.out.println("run TestProcessGetTxCount.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);
        blk = api.getBlockByHash(blk.getHash());

        Message.req_getTransactionCount reqBody = Message.req_getTransactionCount.newBuilder()
                .setBlocknumber(blk.getNumber())
                .setAddress(ByteString.copyFrom(blk.getTransactionsList().get(0).getFrom().toBytes()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_chain_VALUE)
                .put((byte) Message.Funcs.f_getTransactionCount_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getTransactionCount rslt = Message.rsp_getTransactionCount.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getTxCount());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getTransactionCount_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetActiveNodes() {
        System.out.println("run TestProcessGetActiveNodes.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_net_VALUE)
                .put((byte) Message.Funcs.f_getActiveNodes_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getActiveNodes rslt = Message.rsp_getActiveNodes.parseFrom(stripHeader(rsp));
            assertEquals(impl.getAionHub().getP2pMgr().getActiveNodes().size(), rslt.getNodeCount());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getActiveNodes_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetStaticNodes() {
        System.out.println("run TestProcessGetStaticNodes.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_net_VALUE)
                .put((byte) Message.Funcs.f_getStaticNodes_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getStaticNodes rslt = Message.rsp_getStaticNodes.parseFrom(stripHeader(rsp));
            assertEquals(CfgAion.inst().getNodes().length, rslt.getNodeCount());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getStaticNodes_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessGetSolcVersion() {
        System.out.println("run TestProcessGetSolcVersion.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_getSolcVersion_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getSolcVersion rslt = Message.rsp_getSolcVersion.parseFrom(stripHeader(rsp));
            assertEquals(api.solcVersion(), rslt.getVer());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getSolcVersion_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessIsSyncing() {
        System.out.println("run TestProcessIsSyncing.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_net_VALUE)
                .put((byte) Message.Funcs.f_isSyncing_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_isSyncing rslt = Message.rsp_isSyncing.parseFrom(stripHeader(rsp));
            assertNotEquals(impl.isSyncComplete(), rslt.getSyncing());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_isSyncing_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessSyncInfo() {
        System.out.println("run TestProcessSyncInfo.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_net_VALUE)
                .put((byte) Message.Funcs.f_syncInfo_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_syncInfo rslt = Message.rsp_syncInfo.parseFrom(stripHeader(rsp));
            assertNotEquals(impl.isSyncComplete(), rslt.getSyncing());
            assertEquals((long) impl.getLocalBestBlockNumber().orElse(0L), (long) rslt.getChainBestBlock());
            assertEquals((long) impl.getNetworkBestBlockNumber().orElse(0L), (long) rslt.getNetworkBestBlock());
            assertEquals(24, rslt.getMaxImportBlocks());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_syncInfo_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessAccountCreateAndLock() {
        System.out.println("run TestProcessAccountCreateAndLock.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_accountCreate reqBody = Message.req_accountCreate.newBuilder()
                .addPassword("passwd0")
                .addPassword("passwd1")
                .addPassword("passwd2")
                .setPrivateKey(true)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_account_VALUE)
                .put((byte) Message.Funcs.f_accountCreate_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        ByteString addr = ByteString.EMPTY;

        try {
            Message.rsp_accountCreate rslt = Message.rsp_accountCreate.parseFrom(stripHeader(rsp));
            assertEquals(3, rslt.getAddressCount());
            addr = rslt.getAddress(0);
            assertTrue(api.unlockAccount(Address.wrap(rslt.getAddress(0).toByteArray()), "passwd0", 500));
            assertTrue(api.unlockAccount(Address.wrap(rslt.getAddress(1).toByteArray()), "passwd1", 500));
            assertTrue(api.unlockAccount(Address.wrap(rslt.getAddress(2).toByteArray()), "passwd2", 500));
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_accountCreate_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        Message.req_accountlock reqBody2 = Message.req_accountlock.newBuilder()
                .setAccount(addr)
                .setPassword("passwd0")
                .build();

        request = ByteBuffer.allocate(reqBody2.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_wallet_VALUE)
                .put((byte) Message.Funcs.f_accountLock_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody2.toByteArray())
                .array();

        rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);
        assertEquals(0x01, rsp[3]);


        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_accountCreate_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessUserPrivilege() {
        System.out.println("run TestProcessUserPrivilege.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_privilege_VALUE)
                .put((byte) Message.Funcs.f_userPrivilege_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_fail_unsupport_api_VALUE, rsp[1]);

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_userPrivilege_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessMiningValue() {
        System.out.println("run TestProcessMiningValue.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_mine_VALUE)
                .put((byte) Message.Funcs.f_mining_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg)
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_mining rslt = Message.rsp_mining.parseFrom(stripHeader(rsp));
            assertEquals(api.isMining(), rslt.getMining());
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_mining_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessEstimateNrg() {
        System.out.println("run TestProcessEstimateNrg.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] val = {50, 30};

        Message.req_estimateNrg reqBody = Message.req_estimateNrg.newBuilder()
                .setFrom(ByteString.copyFrom(Address.ZERO_ADDRESS().toBytes()))
                .setNrgPrice(5000)
                .setData(ByteString.copyFrom(msg))
                .setValue(ByteString.copyFrom(val))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_estimateNrg_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_estimateNrg rslt = Message.rsp_estimateNrg.parseFrom(stripHeader(rsp));
            assertNotEquals(0, rslt.getNrg());

        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_estimateNrg_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessExportAccounts() {
        System.out.println("run TestProcessExportAccounts.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr1 = new Address(Keystore.create("testPwd1"));
        AccountManager.inst().unlockAccount(addr1, "testPwd1", 50000);

        Address addr2 = new Address(Keystore.create("testPwd2"));
        AccountManager.inst().unlockAccount(addr2, "testPwd12", 50000);

        Message.t_Key tkey1 = Message.t_Key.newBuilder()
                .setAddress(ByteString.copyFrom(addr1.toBytes()))
                .setPassword("testPwd1")
                .build();

        Message.t_Key tkey2 = Message.t_Key.newBuilder()
                .setAddress(ByteString.copyFrom(addr2.toBytes()))
                .setPassword("testPwd2")
                .build();

        Message.req_exportAccounts reqBody = Message.req_exportAccounts.newBuilder()
                .addKeyFile(tkey1)
                .addKeyFile(tkey2)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_account_VALUE)
                .put((byte) Message.Funcs.f_exportAccounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_exportAccounts rslt = Message.rsp_exportAccounts.parseFrom(stripHeader(rsp));
            assertEquals(2, rslt.getKeyFileCount());

        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_exportAccounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessImportAccounts() {
        System.out.println("run TestProcessImportAccounts.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.t_PrivateKey tpkey1 = Message.t_PrivateKey.newBuilder()
                .setPassword("testPwd1")
                .setPrivateKey("pkey1")
                .build();

        Message.t_PrivateKey tpkey2 = Message.t_PrivateKey.newBuilder()
                .setPassword("testPwd2")
                .setPrivateKey("pkey2")
                .build();

        Message.req_importAccounts reqBody = Message.req_importAccounts.newBuilder()
                .addPrivateKey(0, tpkey1)
                .addPrivateKey(1, tpkey2)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_account_VALUE)
                .put((byte) Message.Funcs.f_importAccounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_importAccounts rslt = Message.rsp_importAccounts.parseFrom(stripHeader(rsp));
            assertEquals(0, rslt.getInvalidKeyCount());

        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_importAccounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessRawTransactions() {
        System.out.println("run TestProcessImportAccounts.");

        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        impl.getAionHub().getBlockchain().add(blk);

        Message.req_rawTransaction reqBody = Message.req_rawTransaction.newBuilder()
                .setEncodedTx(ByteString.copyFrom(tx.getEncoded()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_rawTransaction_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_tx_Recved_VALUE, rsp[1]);

        try {
            Message.rsp_sendTransaction rslt = Message.rsp_sendTransaction.parseFrom(stripHeader(rsp));
            assertNotNull(rslt.getTxHash());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_rawTransaction_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessEventRegister() {
        System.out.println("run TestProcessEventRegister.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr1 = new Address(Keystore.create("testPwd1"));
        AccountManager.inst().unlockAccount(addr1, "testPwd1", 50000);

        Address addr2 = new Address(Keystore.create("testPwd2"));
        AccountManager.inst().unlockAccount(addr2, "testPwd12", 50000);

        Message.t_FilterCt fil1 = Message.t_FilterCt.newBuilder()
                .addAddresses(ByteString.copyFrom(addr1.toBytes()))
                .addAddresses(ByteString.copyFrom(addr2.toBytes()))
                .build();

        Message.req_eventRegister reqBody = Message.req_eventRegister.newBuilder()
                .addEvents("event1")
                .setFilter(fil1)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_eventRegister_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_eventRegister rslt = Message.rsp_eventRegister.parseFrom(stripHeader(rsp));
            assertTrue(rslt.getResult());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_eventRegister_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessEventDeregister() {
        System.out.println("run TestProcessEventDeregister.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_eventDeregister reqBody = Message.req_eventDeregister.newBuilder()
                .addEvents("event1")
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_tx_VALUE)
                .put((byte) Message.Funcs.f_eventDeregister_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_eventRegister rslt = Message.rsp_eventRegister.parseFrom(stripHeader(rsp));
            assertFalse(rslt.getResult());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_eventDeregister_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessBlockDetails() {
        System.out.println("run TestProcessEventDeregister.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_getBlockDetailsByNumber reqBody = Message.req_getBlockDetailsByNumber.newBuilder()
                .addBlkNumbers(api.getBestBlock().getNumber())
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_admin_VALUE)
                .put((byte) Message.Funcs.f_getBlockDetailsByNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlockDetailsByNumber rslt = Message.rsp_getBlockDetailsByNumber.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getBlkDetailsCount());
            Message.t_BlockDetail blkdtl = rslt.getBlkDetails(0);
            assertEquals(api.getBestBlock().getNumber(), blkdtl.getBlockNumber());
            assertEquals(api.getBestBlock().getNrgConsumed(), blkdtl.getNrgConsumed());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockDetailsByNumber_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessBlockSqlRange() {
        System.out.println("run TestProcessBlockSqlRange.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        long bestBlkNum = api.getBestBlock().getNumber();

        Message.req_getBlockSqlByRange reqBody = Message.req_getBlockSqlByRange.newBuilder()
                .setBlkNumberStart(bestBlkNum)
                .setBlkNumberEnd(bestBlkNum + 20)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_admin_VALUE)
                .put((byte) Message.Funcs.f_getBlockSqlByRange_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlockSqlByRange rslt = Message.rsp_getBlockSqlByRange.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getBlkSqlCount());
            Message.t_BlockSql blksql = rslt.getBlkSql(0);
            assertEquals(api.getBestBlock().getNumber(), blksql.getBlockNumber());
        }   catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockSqlByRange_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessBlockDetailsRange() {
        System.out.println("run TestProcessBlockDetailsRange.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        long bestBlkNum = api.getBestBlock().getNumber();

        Message.req_getBlockDetailsByRange reqBody = Message.req_getBlockDetailsByRange.newBuilder()
                .setBlkNumberStart(bestBlkNum)
                .setBlkNumberEnd(bestBlkNum + 20)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_admin_VALUE)
                .put((byte) Message.Funcs.f_getBlockDetailsByRange_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlockDetailsByRange rslt = Message.rsp_getBlockDetailsByRange.parseFrom(stripHeader(rsp));
            assertEquals(1, rslt.getBlkDetailsCount());
            Message.t_BlockDetail blksql = rslt.getBlkDetails(0);
            assertEquals(api.getBestBlock().getNumber(), blksql.getBlockNumber());
        }   catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockDetailsByRange_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessBlockDetailsLatest() {
        System.out.println("run TestProcessBlockDetailsLatest.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_getBlockDetailsByLatest reqBody = Message.req_getBlockDetailsByLatest.newBuilder()
                .setCount(20)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_admin_VALUE)
                .put((byte) Message.Funcs.f_getBlockDetailsByLatest_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlockDetailsByLatest rslt = Message.rsp_getBlockDetailsByLatest.parseFrom(stripHeader(rsp));
            assertEquals(20, rslt.getBlkDetailsCount());
            Message.t_BlockDetail blksql = rslt.getBlkDetails(19);
            assertEquals(api.getBestBlock().getNumber(), blksql.getBlockNumber());
        }   catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlockDetailsByLatest_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessBlocksLatest() {
        System.out.println("run TestProcessBlocksLatest.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Message.req_getBlocksByLatest reqBody = Message.req_getBlocksByLatest.newBuilder()
                .setCount(20)
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_admin_VALUE)
                .put((byte) Message.Funcs.f_getBlocksByLatest_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getBlocksByLatest rslt = Message.rsp_getBlocksByLatest.parseFrom(stripHeader(rsp));
            assertEquals(20, rslt.getBlksCount());
            Message.t_Block blksql = rslt.getBlks(19);
            assertEquals(api.getBestBlock().getNumber(), blksql.getBlockNumber());
        }   catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getBlocksByLatest_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

    @Test
    public void TestProcessAccountDetails() {
        System.out.println("run TestProcessAccountDetails.");

        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        Message.req_getAccountDetailsByAddressList reqBody = Message.req_getAccountDetailsByAddressList.newBuilder()
                .addAddresses(ByteString.copyFrom(addr.toBytes()))
                .addAddresses(ByteString.copyFrom(Address.ZERO_ADDRESS().toBytes()))
                .build();

        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_admin_VALUE)
                .put((byte) Message.Funcs.f_getAccountDetailsByAddressList_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(reqBody.toByteArray())
                .array();

        byte[] rsp = api.process(request, socketId);

        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);

        try {
            Message.rsp_getAccountDetailsByAddressList rslt = Message.rsp_getAccountDetailsByAddressList.parseFrom(stripHeader(rsp));
            assertEquals(2, rslt.getAccountsCount());
            Message.t_AccountDetail acctDtl = rslt.getAccounts(0);
            assertEquals(ByteString.copyFrom(addr.toBytes()), acctDtl.getAddress());
            assertEquals(ByteString.copyFrom(api.getBalance(addr).toByteArray()), acctDtl.getBalance());
        }   catch (Exception e) {
            System.out.println(e.getMessage());
            fail();
        }

        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_getAccountDetailsByAddressList_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }

}
