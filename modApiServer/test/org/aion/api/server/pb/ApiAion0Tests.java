package org.aion.api.server.pb;

import org.aion.zero.impl.blockchain.AionImpl;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiAion0Tests {

    byte[] msg;
    int requestSize;

    public ApiAion0Tests() {
        msg = "test message".getBytes();
        requestSize = msg.length + 3;
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

        byte[] request = ByteBuffer.allocate(requestSize).put(api.getApiVersion())
                .put((byte) Message.Servs.s_net_VALUE)
                .put((byte) Message.Funcs.f_protocolVersion_VALUE)
                .put(msg).array();
        assertEquals(Message.Retcode.r_success_VALUE, api.process(request, null)[1]);

        request = ByteBuffer.allocate(requestSize).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_protocolVersion_VALUE)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, null)[1]);


        api.shutDown();
    }

    @Test
    public void TestProcessMinerAddress() {
        System.out.println("run TestProcessMinerAddress.");
        AionImpl impl = AionImpl.inst();
        ApiAion0 api = new ApiAion0(impl);

        byte[] request = ByteBuffer.allocate(requestSize).put(api.getApiVersion())
                .put((byte) Message.Servs.s_wallet_VALUE)
                .put((byte) Message.Funcs.f_minerAddress_VALUE)
                .put(msg).array();
        assertEquals(Message.Retcode.r_success_VALUE, api.process(request, null)[1]);

        request = ByteBuffer.allocate(requestSize).put(api.getApiVersion())
                .put((byte) Message.Servs.s_hb_VALUE)
                .put((byte) Message.Funcs.f_minerAddress_VALUE)
                .put(msg).array();
        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, null)[1]);

        api.shutDown();
    }
//
//    @Test
//    public void TestProcessContractDeploy() {
//        System.out.println("run TestProcessContractDeploy.");
//        AionImpl impl = AionImpl.inst();
//        ApiAion0 api = new ApiAion0(impl);
//
//        byte[] request = ByteBuffer.allocate(requestSize).put(api.getApiVersion())
//                .put((byte) Message.Servs.s_tx_VALUE)
//                .put((byte) Message.Funcs.f_contractDeploy_VALUE)
//                .put(msg).array();
//        assertEquals(Message.Retcode.r_success_VALUE, api.process(request, null)[1]);
//
//        request = ByteBuffer.allocate(requestSize).put(api.getApiVersion())
//                .put((byte) Message.Servs.s_hb_VALUE)
//                .put((byte) Message.Funcs.f_contractDeploy_VALUE)
//                .put(msg).array();
//        assertEquals(Message.Retcode.r_fail_service_call_VALUE, api.process(request, null)[1]);
//
//        api.shutDown();
//    }
}
