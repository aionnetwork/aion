package org.aion.api.server.rpc3;

import java.util.Set;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.types.AionAddress;
import org.aion.rpc.server.PersonalRPC;
import org.aion.rpc.errors.RPCExceptions.*;
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
    public AionAddress personal_ecRecover(ByteArray dataThatWasSigned,
        ByteArray signature) {
        logger.debug("Running personal_ecRecover");
        ISignature signature1 = SignatureFac.fromBytes(signature.toBytes());
        if (signature1 == null) {
            throw InvalidParamsRPCException.INSTANCE;
        }
        byte[] pk = signature1.getAddress();
        if (SignatureFac.verify(dataThatWasSigned.toBytes(), signature1)) {
            return new AionAddress(pk);
        }
        else return null;
    }
}
