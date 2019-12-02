package org.aion.api.server.rpc3;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import org.aion.rpc.errors.RPCExceptions.MethodNotFoundRPCException;
import org.aion.rpc.errors.RPCExceptions.RpcCodeEnums;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.PongEnum;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.Response;
import org.aion.rpc.types.RPCTypes.RpcError;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.AddressConverter;
import org.aion.rpc.types.RPCTypesConverter.PongEnumConverter;
import org.aion.rpc.types.RPCTypesConverter.RequestListConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseListConverter;
import org.aion.rpc.types.RPCTypesConverter.RpcErrorConverter;
import org.junit.Before;
import org.junit.Test;

public class Web3EntryPointTest {

    private Web3EntryPoint web3EntryPoint;
    private ChainHolder holder = mock(ChainHolder.class);
    private ByteArray byteArray = new ByteArray("0x000111");
    @Before
    public void setup(){
        doReturn(byteArray.toBytes()).when(holder).getSeed();
        RPCServerMethods rpc = new RPCMethods(holder);
        web3EntryPoint = new Web3EntryPoint(rpc,
            List.of("personal","ops", "stratum"),
            List.of("personal_ecRecover"), List.of("notAnRPC"),
            Executors.newSingleThreadExecutor());
    }

    @Test
    public void call() {
        String response = web3EntryPoint.executeSingle("{\"jsonRPC\":\"2.0\",\"method\":\"personal_ecRecover\",\"params\": [\"0x48656c6c6f20576f726c64\",\"0x7a142a509e6a5e41f82c2e3efb94b05f2a5ba371c8360b58f0ad7b20cd6f8288282f36ab908e5222b8c359d3cc0bacc5f155a952a7a25a1c7c10721b144f134c29e4bb1d31ed3f2642ca1f35acb36c297958a593775f98e7048afc4a1a72de0d\"]}");
        String result = AddressConverter.decode(ResponseConverter.decode(response).result.toString()).toString();
        assertEquals("a0d4b5370a3c949b46cef55f625e3be1e046a4cec5c5b924a2f91794d450eda9", result);
    }

    @Test
    public void testFailCall(){
        String response = web3EntryPoint.executeSingle("{\"jsonRPC\":\"2.0\",\"method\":\"personal_eecover\",\"params\": [\"0x48656c6c6f20576f726c64\",\"0x7a142a509e6a5e41f82c2e3efb94b05f2a5ba371c8360b58f0ad7b20cd6f8288282f36ab908e5222b8c359d3cc0bacc5f155a952a7a25a1c7c10721b144f134c29e4bb1d31ed3f2642ca1f35acb36c297958a593775f98e7048afc4a1a72de0d\"]}");
        RpcError error = ResponseConverter.decode(response).error;
        assertNotNull(error);
        assertEquals(MethodNotFoundRPCException.INSTANCE.getMessage(),
            RpcErrorConverter.encodeStr(error));
    }

    @Test
    public void isExecutable() {
        assertTrue(web3EntryPoint.isExecutable("getseed"));
        assertTrue(web3EntryPoint.isExecutable("submitsignature"));
        assertTrue(web3EntryPoint.isExecutable("submitseed"));
        assertTrue(web3EntryPoint.isExecutable("personal_ecRecover"));
        assertTrue(web3EntryPoint.isExecutable("ops_getBlockDetails"));
        assertFalse(web3EntryPoint.isExecutable("notAnRPC"));
    }

    @Test
    public void checkMethod() {
        assertTrue(web3EntryPoint.checkMethod("personal_ecRecover"));
        assertTrue(web3EntryPoint.checkMethod("ops_getBlockDetails"));
        assertFalse(web3EntryPoint.checkMethod("notAnRPC"));
        assertTrue(web3EntryPoint.checkMethod("ep"));
    }

    @Test
    public void executeBatch() {
        // Test that we can successfully perform a batch of requests
        final Request request = new Request(1, "ping", null, VersionType.Version2);
        final List<Request> requests = Collections.nCopies(10, request);

        List<Response> responses = ResponseListConverter.decode(web3EntryPoint.executeBatch(
            RequestListConverter.encodesStr(requests)));

        assertTrue(responses.stream().allMatch(r-> PongEnumConverter.decode(r.result) == PongEnum.PONG));

        // a null request should result in an invalid request error
        responses = ResponseListConverter.decode(web3EntryPoint.executeBatch(null));

        assertTrue(responses.size() == 1 && responses.stream().allMatch(
            r-> PongEnumConverter.decode(r.result) == null &&
                r.error.code == RpcCodeEnums.InvalidRequest_CODE.code // check that the error has
                                                                      // the expected error code
        ));
    }
}