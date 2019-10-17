package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;

import org.aion.api.server.rpc3.types.RPCTypes.EcRecoverParams;
import org.aion.api.server.rpc3.types.RPCTypes.Request;
import org.aion.api.server.rpc3.types.RPCTypes.VersionType;
import org.aion.api.server.rpc3.types.RPCTypesConverter.AionAddressConverter;
import org.aion.api.server.rpc3.types.RPCTypesConverter.DataHexStringConverter;
import org.aion.api.server.rpc3.types.RPCTypesConverter.EcRecoverParamsConverter;
import org.aion.api.server.rpc3.types.RPCTypesConverter.ResponseConverter;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PersonalRPCImplTest {
    private PersonalRPCImpl rpc = new PersonalRPCImpl();

    @Before
    public void setup() {
        rpc = Mockito.spy(new PersonalRPCImpl());
    }

    @Test
    public void personal_ecRecover() {
        ECKey ecKey = ECKeyFac.inst().create();
        String helloMessage = "Hello World";
        String byeMessage = "Bye World";
        ByteArrayWrapper helloByteMessage = ByteArrayWrapper.wrap(helloMessage.getBytes());
        ByteArrayWrapper byeByteMessage = ByteArrayWrapper.wrap(byeMessage.getBytes());

        //Create the signed message
        ByteArrayWrapper signedMessage =
                ByteArrayWrapper.wrap(ecKey.sign(helloByteMessage.toBytes()).toBytes());

        //append 0x to make params valid
        String hexString = "0x" + ByteUtil.toHexString(signedMessage.toBytes());
        String pubKey = "0x" + ByteUtil.toHexString(ecKey.getAddress());

        //recover public key and validate that the message was signed
        AionAddress recoveredKey = rpc.personal_ecRecover(helloByteMessage, DataHexStringConverter.decode(hexString));
        System.out.printf("Public Key: %s %nRecovered Key: %s%n ", DataHexStringConverter.decode(pubKey), recoveredKey);
        assertEquals(AionAddressConverter.decode(pubKey), recoveredKey);

        //attempt to recover the public key and fail since the incorrect message was used
        recoveredKey = rpc.personal_ecRecover(byeByteMessage, DataHexStringConverter.decode(hexString));
        System.out.printf("Public Key: %s %nRecovered Key: %s%n ", DataHexStringConverter.decode(pubKey), recoveredKey);
        assertNotEquals(AionAddressConverter.decode(pubKey), recoveredKey);

        try{
            //attempt to recover a pk with an incorrect signature
            rpc.personal_ecRecover(helloByteMessage, DataHexStringConverter.decode(hexString+"1"));
            fail();
        }catch (Exception e){
            //We expect an exception
        }
    }

    @Test
    public void executePersonal_ecRecover() {
        ECKey ecKey = ECKeyFac.inst().create();
        String helloMessage = "Hello World";
        ByteArrayWrapper helloByteMessage = ByteArrayWrapper.wrap(helloMessage.getBytes());

        // Create the signed message
        ByteArrayWrapper signedMessage =
            ByteArrayWrapper.wrap(ecKey.sign(helloByteMessage.toBytes()).toBytes());

        // append 0x to make params valid
        String pubKey = "0x" + ByteUtil.toHexString(ecKey.getAddress());
        // well formed request
        Request request =
            new Request(
                2,
                "personal_ecRecover",
                EcRecoverParamsConverter.encode(
                    new EcRecoverParams(helloByteMessage, signedMessage)),
                VersionType.Version2);
        assertEquals(pubKey, ResponseConverter.decode(rpc.execute(request)).result);
        // incorrect method name
        request =
            new Request(
                2,
                "personal_ecRecovery",
                EcRecoverParamsConverter.encode(
                    new EcRecoverParams(helloByteMessage, signedMessage)),
                VersionType.Version2);
        assertEquals(
            new RPCExceptions.MethodNotFoundRPCException().getMessage(),
            rpc.execute(request));
        // incorrect params
        request = new Request(2, "personal_ecRecover", "[]", VersionType.Version2);
        assertEquals(
            new RPCExceptions.InvalidParamsRPCException().getMessage(),
            rpc.execute(request));

        Mockito.doThrow(NullPointerException.class).when(rpc).personal_ecRecover(any(), any());
        // well formed request but fails internally
        request =
            new Request(
                2,
                "personal_ecRecover",
                EcRecoverParamsConverter.encode(
                    new EcRecoverParams(helloByteMessage, signedMessage)),
                VersionType.Version2);
        assertEquals(
            new RPCExceptions.InternalErrorRPCException().getMessage(),
            rpc.execute(request));
    }

    @Test
    public void testIsExecutable(){
        assertTrue(rpc.isExecutable("personal_ecRecover"));
        assertFalse(rpc.isExecutable("notRPC"));
    }
}
