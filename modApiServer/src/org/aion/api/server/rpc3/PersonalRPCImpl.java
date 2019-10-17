package org.aion.api.server.rpc3;

import java.util.Objects;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.util.types.ByteArrayWrapper;

public class PersonalRPCImpl extends PersonalRPC {

    @Override
    protected ByteArrayWrapper personal_ecRecover(String dataThatWasSigned,
        ByteArrayWrapper signature) {
        ISignature signature1 = Ed25519Signature.fromBytes(signature.toBytes());
        if (signature1 == null) {
            throw new RPCExceptions.InvalidParamsRPCException();
        }
        byte[] pk = signature1.getPubkey(dataThatWasSigned.getBytes());
        return ByteArrayWrapper.wrap(Objects.requireNonNullElseGet(pk, () -> new byte[0]));
    }
}
