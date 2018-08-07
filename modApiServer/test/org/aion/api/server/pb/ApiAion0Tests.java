package org.aion.api.server.pb;

import com.google.protobuf.ByteString;
import org.aion.api.impl.internal.ApiUtils;
import org.aion.api.server.ApiUtil;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.TypeConverter;
import org.aion.equihash.EquihashMiner;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
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
                .put((byte) Message.Funcs.f_accounts_VALUE)
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
                .put((byte) Message.Funcs.f_accounts_VALUE)
                .put((byte) 1)
                .put(hash)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, socketId)[1]);

        api.shutDown();
    }
}
