package org.aion.api.server.rpc3;


import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.aion.rpc.types.RPCTypesConverter.RequestListConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseListConverter;
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
    private final ExecutorService es; // used to execute the JSON RPC batch requests
    private final Duration terminationWait;
    static {
        //An immutable map that maps a method to an interface name
        methodInterfaceMap = Collections.unmodifiableMap(RPCServerMethods.methodInterfaceMap());
    }

    /**
     *
     * @param rpc an implementation of {@link RPCServerMethods}
     * @param enabledGroup a list containing all enabled interfaces
     * @param enabledMethods a list containing all enabled methods
     * @param disabledMethods a list containing all disabled methods
     * @param executorService the executor to be used to execute batch requests asynchronously
     */
    public Web3EntryPoint(RPCServerMethods rpc, List<String> enabledGroup,
        List<String> enabledMethods,
        List<String> disabledMethods,
        ExecutorService executorService){
        this.rpc = rpc;
        this.enabledGroup = Set.copyOf(enabledGroup);
        this.enabledMethods = Set.copyOf(enabledMethods);
        this.disabledMethods = Set.copyOf(disabledMethods);
        this.es = executorService;
        terminationWait = Duration.of(5, ChronoUnit.SECONDS);
    }

    /**
     *
     * @param rpc an implementation of {@link RPCServerMethods}
     * @param enabledGroup a list containing all enabled interfaces
     * @param enabledMethods a list containing all enabled methods
     * @param disabledMethods a list containing all disabled methods
     * @param executorService the executor to be used to execute batch requests asynchronously
     * @param terminationWait the length of time to wait before force closing an instance of {@link Web3EntryPoint}
     */
    public Web3EntryPoint(RPCServerMethods rpc, Set<String> enabledGroup,
        Set<String> enabledMethods, Set<String> disabledMethods,
        ExecutorService executorService, Duration terminationWait) {
        this.rpc = rpc;
        this.enabledGroup = enabledGroup;
        this.enabledMethods = enabledMethods;
        this.disabledMethods = disabledMethods;
        this.es = executorService;
        this.terminationWait = terminationWait;
    }

    /**
     * Used to execute a batch request
     * @param requestsString the request string representing a list of json rpc requests
     * @return an encoded response list
     */
    public String executeBatch(String requestsString) {
        try {
            Stopwatch stopwatch = null;
            if (logger.isDebugEnabled()){
                stopwatch = Stopwatch.createStarted();
            }

            List<Request> requests = RequestListConverter.decode(requestsString);
            if (requests == null) {
                throw InvalidRequestRPCException.INSTANCE;
            }

            Stream<CompletableFuture<Response>> rpcCalls =
                    requests.stream()
                            .map(request -> asyncCall(() -> call(request), request)); // execute the supplier asynchronously

            // join all the responses and encode the response as a string
            String encodedResponseList = ResponseListConverter.encodesStr(
                    rpcCalls.map(this::extractResponseFromFuture).collect(Collectors.toList()));

            if (stopwatch != null){
                logger.debug("Executed {} requests and produced: {}bytes in <{}>ms",
                    requests.size(),
                    encodedResponseList.getBytes().length,
                    stopwatch.elapsed().toMillis());
            }
            return encodedResponseList;
        } catch (InvalidRequestRPCException e) {
            // if we throw a parse error the request was badly formed.
            return ResponseListConverter.encodesStr(
                Collections.singletonList(new Response(
                    null,
                    null,
                    e.getError(),
                    VersionType.Version2)));
        }
    }

    /**
     * @param responseSupplier an json rpc response supplier
     * @return a future wrapping this async task
     */
    private CompletableFuture<Response> asyncCall(Supplier<Response> responseSupplier, Request request){
        return CompletableFuture.supplyAsync(responseSupplier, es)
                .exceptionally(
                        th -> {
                            RpcError error = errorFromException(th);
                            Integer id = request == null ? null : request.id;
                            return new Response(id, null, error, VersionType.Version2);
                        });
    }

    /**
     *
     * @param responseCompletableFuture the async call being executed
     * @return a kernel response
     */
    private Response extractResponseFromFuture(CompletableFuture<Response> responseCompletableFuture){
        try{
            return responseCompletableFuture.join();
        }catch (RuntimeException e){
            RpcError err= errorFromException(e);
            return new Response(null, null, err, VersionType.Version2);
        }
    }

    /**
     * Creates a formatted internal rpc error from a throwable
     * @param throwable the throwable to be handled
     * @return an rpc error
     */
    private RpcError errorFromException(Throwable throwable) {
        // Prefer this over creating an instance of RpcError
        return new InternalErrorRPCException(
            throwable.getClass().getSimpleName() + ":" + throwable.getMessage()).getError();
    }

    /**
     * Executes a single rpc request
     * @param request client request
     * @return the json rpc responses
     */
    public Response call(Request request){
        if (request == null) {
            return new Response(null, null, InvalidRequestRPCException.INSTANCE.getError(), VersionType.Version2);
        }

        Object resultUnion =null;
        Integer id = request.id;
        RpcError err = null;
        try{
            if (checkMethod(request.method)){
                resultUnion = RPCServerMethods.execute(request, rpc);
            } else {
                logger.debug(
                        "Request attempted to call a method on a disabled interface: {}",
                        request.method);
                err = InvalidRequestRPCException.INSTANCE.getError();
            }
        }
        catch (RPCException e){
            logger.debug("Request failed due to an RPC exception: {}", e.getMessage());
            err = e.getError();
        }
        catch (Exception e){
            logger.error("Call to {} failed.", request.method);
            logger.error("Request failed due to an internal error: ", e);
            err= errorFromException(e);
        }
        return new Response(id, resultUnion, err, VersionType.Version2);
    }

    /**
     *
     * @param requestString the single rpc request to be executed
     * @return an encoded response
     */
    public String executeSingle(String requestString){
        logger.debug("Received request: {}",requestString);
        Integer id = null;
        Stopwatch stopwatch = null;
        Response response;
        if (logger.isDebugEnabled()){
            stopwatch = Stopwatch.createStarted();
        }

        try{
            Request request = readRequest(requestString);
            id = request.id;
            response = call(request);
        }catch (RPCException e){
            logger.debug("Request failed due to an RPC exception: {}", e.getMessage());
            response= new Response(id, null, e.getError(), VersionType.Version2);
            //Don't log this error since it may already be logged elsewhere
        }
        final String resultString = ResponseConverter.encodeStr(response);
        if ( stopwatch != null ) {
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

    public void shutdown(){
        es.shutdown();
        try{
            es.awaitTermination(terminationWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignore interruption
        } finally{
            es.shutdownNow();
        }
    }
}
