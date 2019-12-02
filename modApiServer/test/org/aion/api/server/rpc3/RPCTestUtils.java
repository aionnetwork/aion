package org.aion.api.server.rpc3;

import java.util.function.Function;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypesConverter.RequestConverter;

public class RPCTestUtils {

    /**
     * @param request the request object to be executed
     * @param rpcMethods an implementation of {@link RPCServerMethods}
     * @param extractor the decoded
     * @param <T> the type of the RPC result
     * @return the RPC result
     */
    public static <T> T executeRequest(
            Request request, RPCServerMethods rpcMethods, Function<Object, T> extractor) {
        return extractor.apply(
                RPCServerMethods.execute(
                        RequestConverter.decode(
                                RequestConverter.encodeStr(
                                        request)), // encode and decode the request to validate the
                                                   // request object
                        rpcMethods));
    }
}
