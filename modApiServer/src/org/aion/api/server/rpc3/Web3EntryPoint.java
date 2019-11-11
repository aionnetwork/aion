package org.aion.api.server.rpc3;


import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rpc.errors.RPCExceptions.InternalErrorRPCException;
import org.aion.rpc.errors.RPCExceptions.InvalidRequestRPCException;
import org.aion.rpc.errors.RPCExceptions.RPCException;
import org.aion.rpc.server.RPC;
import org.aion.rpc.types.RPCTypes.RPCError;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.Response;
import org.aion.rpc.types.RPCTypes.ResultUnion;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.RequestConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseConverter;
import org.slf4j.Logger;

public class Web3EntryPoint {

    private final RPC rpc;
    private final Set<String> enabledGroup;
    private final Set<String> enabledMethods;
    private final Set<String> disabledMethods;
    private static final Logger logger = AionLoggerFactory.getLogger(LogEnum.API.name());
    //TODO this map should eventually be removed
    //TODO this is currently used only to allow support of existing tooling
    private static final Map<String,String> methodInterfaceMap;
    static {
        methodInterfaceMap = Map
            .ofEntries(Map.entry("getseed", "stratum_getseed"),
                Map.entry("submitseed", "stratum_submitseed"),
                Map.entry("submitsignature", "stratum_submitsignature"),
                Map.entry("getblocktemplate", "stratum_getblocktemplate"),
                Map.entry("submitblock", "stratum_submitblock"),
                Map.entry("submitvalidateaddress", "stratum_validateaddress"),
                Map.entry("getDifficulty", "stratum_getDifficulty"),
                Map.entry("getMinerStats", "stratum_getMinerStats"));
    }

    public Web3EntryPoint(RPC rpc, List<String> enabledGroup, List<String> enabledMethods,
        List<String> disabledMethods){
        this.rpc = rpc;
        this.enabledGroup = Set.copyOf(enabledGroup);
        this.enabledMethods = Set.copyOf(enabledMethods);
        this.disabledMethods = Set.copyOf(disabledMethods);
    }

    public String call(String requestString){
        logger.debug("Received request: {}",requestString);
        Request request = null;
        RPCError err = null;
        Integer id = null;
        ResultUnion resultUnion = null;
        try{
            request = readRequest(requestString);

            id = request.id;
            if (checkMethod(request.method)){
                resultUnion = rpc.execute(request);
            }else {
                logger.debug(
                        "Request attempted to call a method on a disabled interface: {}",
                        request.method);
                err= InvalidRequestRPCException.INSTANCE.getError();
            }
        }catch (InvalidRequestRPCException e){
            err = e.getError();//Don't log this error since it may already be logged elsewhere
        }
        catch (RPCException e){
            logger.debug("Request failed due to an RPC exception: ", e);
            err = e.getError();
        }
        catch (Exception e){
            logger.debug("Call to {} failed.", request==null? "null":request.method);
            logger.debug("Request failed due to an internal error: ", e);
            err= InternalErrorRPCException.INSTANCE.getError();
        }
        final String resultString = ResponseConverter
            .encodeStr(new Response(id, resultUnion, err, VersionType.Version2));
        logger.debug("Produced response: {}", requestString);
        return resultString;
    }

    private static Request readRequest(String requestString) {
        Request request;
        try{
            request = RequestConverter.decode(requestString);
        }catch (Exception e){
            logger.debug("Received an invalid request: {}", requestString);
            throw InvalidRequestRPCException.INSTANCE;
        }
        if (request==null) throw InvalidRequestRPCException.INSTANCE;
        return request;
    }

    public boolean isExecutable(String method){
        // A small hack to enforce an interface name for the block validation API
        String interfaceName = methodInterfaceMap.containsKey(method) ?
            methodInterfaceMap.get(method).split("_")[0] : method.split("_")[0];
        return enabledGroup.contains(interfaceName) && rpc.isExecutable(method);
    }

    public boolean checkMethod(String method){
       if (enabledMethods !=null && enabledMethods.contains(method)){
           return true;
       }
       else
           return disabledMethods == null || !disabledMethods.contains(method);
    }
}
