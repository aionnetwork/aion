package org.aion.api.server.rpc3;


import com.google.common.base.Stopwatch;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rpc.errors.RPCExceptions.InternalErrorRPCException;
import org.aion.rpc.errors.RPCExceptions.InvalidRequestRPCException;
import org.aion.rpc.errors.RPCExceptions.RPCException;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.Response;
import org.aion.rpc.types.RPCTypes.RpcError;
import org.aion.rpc.types.RPCTypes.VersionType;
import org.aion.rpc.types.RPCTypesConverter.RequestConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseConverter;
import org.slf4j.Logger;

public class Web3EntryPoint {

    private final RPCServerMethods rpc;
    private final Set<String> enabledGroup;
    private final Set<String> enabledMethods;
    private final Set<String> disabledMethods;
    private static final Logger logger = AionLoggerFactory.getLogger(LogEnum.API.name());
    //TODO this map should eventually be removed
    //TODO this is currently used only to allow support of existing tooling
    private static final Map<String,String> methodInterfaceMap;
    static {
        //An immutable map that maps a method to an interface name
        methodInterfaceMap = Collections.unmodifiableMap(RPCServerMethods.methodInterfaceMap());
    }

    public Web3EntryPoint(RPCServerMethods rpc, List<String> enabledGroup, List<String> enabledMethods,
        List<String> disabledMethods){
        this.rpc = rpc;
        this.enabledGroup = Set.copyOf(enabledGroup);
        this.enabledMethods = Set.copyOf(enabledMethods);
        this.disabledMethods = Set.copyOf(disabledMethods);
    }

    public String call(String requestString){
        logger.debug("Received request: {}",requestString);
        Request request = null;
        RpcError err = null;
        Integer id = null;
        Object resultUnion = null;
        Stopwatch stopwatch = null;
        if (logger.isDebugEnabled()){
            stopwatch = Stopwatch.createStarted();
        }

        try{
            request = readRequest(requestString);
            logger.info("Executing: {}", request.method);
            id = request.id;
            if (checkMethod(request.method)){
                resultUnion = RPCServerMethods.execute(request, rpc);
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
            logger.error("Request failed due to an RPC exception: {}", e.getMessage());
            err = e.getError();
        }
        catch (Exception e){
            logger.error("Call to {} failed.", request==null? "null":request.method);
            logger.error("Request failed due to an internal error: ", e);
            err= new InternalErrorRPCException(
                e.getClass().getSimpleName()+":"+e.getMessage()).getError(); // Prefer this over
                                                                // creating an instance of RpcError
        }
        final String resultString = ResponseConverter
            .encodeStr(new Response(id, resultUnion, err, VersionType.Version2));

        if (stopwatch != null) {
            logger.debug("Produced response: {}bytes in <{}>ms",
                resultString.getBytes().length,
                stopwatch.elapsed().toMillis());
        }
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
        String interfaceName = methodInterfaceMap.getOrDefault(method,"");
        return (enabledGroup.contains(interfaceName) || // check that method is enabled
            interfaceName.replaceAll("\\W","").isEmpty()) // allow methods that do not belong to an interface
            && rpc.isExecutable(method);
    }

    public boolean checkMethod(String method){
       if (enabledMethods !=null && enabledMethods.contains(method)){
           return true;
       }
       else
           return disabledMethods == null || !disabledMethods.contains(method);
    }
}
