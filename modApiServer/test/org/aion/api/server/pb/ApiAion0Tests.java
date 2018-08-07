package org.aion.api.server.pb;

import com.google.protobuf.ByteString;
import org.aion.api.impl.internal.ApiUtils;
import org.aion.api.server.ApiUtil;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.equihash.EquihashMiner;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ApiAion0Tests {

    byte[] msg, socketId, hash;
    int requestSize;

    private static final int MSG_HASH_LEN = 8;
    private static final int RSP_HEADER_NOHASH_LEN = 3;
    public static final int REQ_HEADER_NOHASH_LEN = 4;
    private static final int RSP_HEADER_LEN = RSP_HEADER_NOHASH_LEN + MSG_HASH_LEN;

    public ApiAion0Tests() {
        msg = "test message".getBytes();
        socketId = RandomUtils.nextBytes(5);
        hash = RandomUtils.nextBytes(ApiUtil.HASH_LEN);
        requestSize = msg.length + 3;
    }

    private byte[] stripHeader(byte[] rsp) {
        boolean hasHash = (rsp[2] == 1 ? true : false);
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

//    @Test
//    public void TestProcessGetTR() {
//        System.out.println("run TestProcessGetTR.");
//        AionImpl impl = AionImpl.inst();
//        ApiAion0 api = new ApiAion0(impl);
//
//        Address addr = new Address(Keystore.create("testPwd"));
//        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);
//        AionTransaction tx = new AionTransaction("1".getBytes(), addr, "500".getBytes(), msg, 5000, 3000);
//        tx.sign(new ECKeyEd25519());
//
//        AionTxInfo;
//
//        ((AionRepositoryImpl) impl.getAionHub().getRepository()).getTransactionStore().putToBatch()
//
//        Message.req_getTransactionReceipt reqBody = Message.req_getTransactionReceipt.newBuilder()
//                .setTxHash(ByteString.copyFrom(tx.getHash()))
//                .build();
//
//        byte[] request = ByteBuffer.allocate(reqBody.getSerializedSize() + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
//                .put((byte) Message.Servs.s_tx_VALUE)
//                .put((byte) Message.Funcs.f_getTransactionReceipt_VALUE)
//                .put((byte) 1)
//                .put(hash)
//                .put(reqBody.toByteArray())
//                .array();
//
//        byte[] rsp = api.process(request, socketId);
//
//        assertEquals(Message.Retcode.r_success_VALUE, rsp[1]);
//
//        try {
//            Message.rsp_getTransactionReceipt rslt = Message.rsp_getTransactionReceipt.parseFrom(stripHeader(rsp));
//            assertEquals(ByteString.copyFrom(addr.toBytes()), rslt.getTo());
//        } catch (Exception e) {
//            System.out.println(e.getMessage());
//            fail();
//        }
//
//        request = ByteBuffer.allocate(msg.length + REQ_HEADER_NOHASH_LEN + hash.length).put(api.getApiVersion())
//                .put((byte) Message.Servs.s_hb_VALUE)
//                .put((byte) Message.Funcs.f_getTransactionReceipt_VALUE)
//                .put((byte) 1)
//                .put(hash)
//                .put(msg).array();
//        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);
//
//        api.shutDown();
//    }
}
