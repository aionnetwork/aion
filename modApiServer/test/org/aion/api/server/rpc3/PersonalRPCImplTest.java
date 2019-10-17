package org.aion.api.server.rpc3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.aion.api.server.rpc3.types.RPCTypesConverter.DataHexStringConverter;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Test;

public class PersonalRPCImplTest {
    private PersonalRPC rpc = new PersonalRPCImpl();

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
        ByteArrayWrapper recoveredKey = rpc.personal_ecRecover(helloByteMessage, DataHexStringConverter.decode(hexString));
        System.out.printf("Public Key: %s %nRecovered Key: %s%n ", DataHexStringConverter.decode(pubKey), recoveredKey);
        assertEquals(DataHexStringConverter.decode(pubKey), recoveredKey);

        //attempt to recover the public key and fail since the incorrect message was used
        recoveredKey = rpc.personal_ecRecover(byeByteMessage, DataHexStringConverter.decode(hexString));
        System.out.printf("Public Key: %s %nRecovered Key: %s%n ", DataHexStringConverter.decode(pubKey), recoveredKey);
        assertNotEquals(DataHexStringConverter.decode(pubKey), recoveredKey);

        try{
            //attempt to recover a pk with an incorrect signature
            rpc.personal_ecRecover(helloByteMessage, DataHexStringConverter.decode(hexString+"1"));
            fail();
        }catch (Exception e){
            //We expect an exception
        }
    }
}
