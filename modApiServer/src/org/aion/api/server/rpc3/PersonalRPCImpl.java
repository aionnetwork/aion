package org.aion.api.server.rpc3;

import java.util.Objects;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.util.types.ByteArrayWrapper;

public class PersonalRPCImpl extends PersonalRPC {

    /**
     * Recovers the public key using the signature and the message
     * @param dataThatWasSigned the message
     * @param signature the signature
     * @return a byte array containing the public key
     */
    @Override
    ByteArrayWrapper personal_ecRecover(ByteArrayWrapper dataThatWasSigned,
        ByteArrayWrapper signature) {
        ISignature signature1 = SignatureFac.fromBytes(signature.toBytes());
        if (signature1 == null) {
            throw new RPCExceptions.InvalidParamsRPCException();
        }
        byte[] pk = signature1.getAddress();
        if (SignatureFac.verify(dataThatWasSigned.toBytes(), signature1)) {
            return ByteArrayWrapper.wrap(Objects.requireNonNullElseGet(pk, () -> new byte[0]));
        }
        else return ByteArrayWrapper.wrap(new byte[0]);
    }
}
