package org.aion.api.server.rpc3;

import java.util.Objects;
import java.util.Set;
import org.aion.api.server.rpc3.types.RPCTypes.Request;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

public class PersonalRPCImpl implements PersonalRPC {
    private final Logger logger = AionLoggerFactory.getLogger(LogEnum.API.name());
    private final Set<String> methods = listMethods();

    @Override
    public boolean isExecutable(String method) {
        return methods.contains(method);
    }

    /**
     * Recovers the public key using the signature and the message
     * @param dataThatWasSigned the message
     * @param signature the signature
     * @return a byte array containing the public key
     */
    @Override
    public AionAddress personal_ecRecover(ByteArrayWrapper dataThatWasSigned,
        ByteArrayWrapper signature) {
        logger.debug("Running personal_ecRecover");
        ISignature signature1 = SignatureFac.fromBytes(signature.toBytes());
        if (signature1 == null) {
            throw new RPCExceptions.InvalidParamsRPCException();
        }
        byte[] pk = signature1.getAddress();
        if (SignatureFac.verify(dataThatWasSigned.toBytes(), signature1)) {
            return new AionAddress(pk);
        }
        else return null;
    }
}
