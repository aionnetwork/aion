package org.aion.api.server.rpc3;

import java.util.Set;
import org.aion.rpc.errors.RPCExceptions.UnsupportedUnityFeatureRPCException;
import org.aion.rpc.server.StratumRPC;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.types.AionAddress;

public class StratumRPCImpl implements StratumRPC {

    private final Set<String> methods = listMethods();
    private final ChainHolder chainHolder;

    public StratumRPCImpl(ChainHolder chainHolder) {
        this.chainHolder = chainHolder;
    }

    @Override
    public boolean isExecutable(String method) {
        return methods.contains(method);
    }

    @Override
    public ByteArray stratum_getSeed() {
        try {
            byte[] result = chainHolder.getSeed();
            if (result == null) {
                return null;
            } else {
                return ByteArray.wrap(result);
            }
        } catch (UnsupportedOperationException e) {
            throw UnsupportedUnityFeatureRPCException.INSTANCE;
        }
    }

    @Override
    public ByteArray stratum_submitSeed(
            ByteArray newSeed, ByteArray signingPublicKey, AionAddress coinBase) {
        try {
            byte[] result =
                    chainHolder.submitSeed(
                            newSeed.toBytes(), signingPublicKey.toBytes(), coinBase.toByteArray());
            if (result == null) {
                return null;
            } else {
                return ByteArray.wrap(result);
            }
        } catch (UnsupportedOperationException e) {
            throw UnsupportedUnityFeatureRPCException.INSTANCE;
        }
    }

    @Override
    public Boolean stratum_submitSignature(ByteArray signature, ByteArray sealHash) {
        try{
            return chainHolder.submitSignature(signature.toBytes(), sealHash.toBytes());
        }catch (UnsupportedOperationException e){
            throw UnsupportedUnityFeatureRPCException.INSTANCE;
        }
    }
}
