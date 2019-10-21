package org.aion.api.server.rpc3;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import org.aion.api.server.rpc3.RPCExceptions.MethodNotFoundRPCException;
import org.aion.api.server.rpc3.types.RPCTypes.Error;
import org.aion.api.server.rpc3.types.RPCTypesConverter.ErrorConverter;
import org.aion.api.server.rpc3.types.RPCTypesConverter.ResponseConverter;
import org.junit.Before;
import org.junit.Test;

public class Web3EntryPointTest {

    private Web3EntryPoint web3EntryPoint;

    @Before
    public void setup(){
        web3EntryPoint = new Web3EntryPoint(new PersonalRPCImpl(), List.of("personal"), List.of("personal_ecRecover"),
            List.of("notAnRPC"));
    }

    @Test
    public void call() {
        String response = web3EntryPoint.call("{\"jsonRPC\":\"2.0\",\"method\":\"personal_ecRecover\",\"params\": [\"0x48656c6c6f20576f726c64\",\"0x7a142a509e6a5e41f82c2e3efb94b05f2a5ba371c8360b58f0ad7b20cd6f8288282f36ab908e5222b8c359d3cc0bacc5f155a952a7a25a1c7c10721b144f134c29e4bb1d31ed3f2642ca1f35acb36c297958a593775f98e7048afc4a1a72de0d\"]}");
        String result = ResponseConverter.decode(response).result.toString();
        assertEquals("0xa0d4b5370a3c949b46cef55f625e3be1e046a4cec5c5b924a2f91794d450eda9", result);
    }

    public void testFailCall(){
        String response = web3EntryPoint.call("{\"jsonRPC\":\"2.0\",\"method\":\"personal_eecover\",\"params\": [\"0x48656c6c6f20576f726c64\",\"0x7a142a509e6a5e41f82c2e3efb94b05f2a5ba371c8360b58f0ad7b20cd6f8288282f36ab908e5222b8c359d3cc0bacc5f155a952a7a25a1c7c10721b144f134c29e4bb1d31ed3f2642ca1f35acb36c297958a593775f98e7048afc4a1a72de0d\"]}");
        Error error = ResponseConverter.decode(response).error;
        assertNotNull(error);
        assertEquals(MethodNotFoundRPCException.INSTANCE.getMessage(), ErrorConverter.encode(error));
    }

    @Test
    public void isExecutable() {
        assertTrue(web3EntryPoint.isExecutable("personal_ecRecover"));
        assertFalse(web3EntryPoint.isExecutable("notAnRPC"));
    }

    @Test
    public void checkMethod() {
        assertTrue(web3EntryPoint.checkMethod("personal_ecRecover"));
        assertFalse(web3EntryPoint.checkMethod("notAnRPC"));
        assertTrue(web3EntryPoint.checkMethod("ep"));
    }
}