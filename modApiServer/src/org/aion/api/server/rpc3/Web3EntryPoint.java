package org.aion.api.server.rpc3;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.aion.api.server.rpc3.types.RPCTypes.Request;
import org.aion.api.server.rpc3.types.RPCTypesConverter.RequestConverter;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class Web3EntryPoint {

    private final Set<String> enabledMethods;
    private final Set<String> disabledMethods;
    private Map<String, Function<Request, String>> groupMap;
    private Map<String, Predicate<String>> executablePredicateMaps;
    private Logger logger = AionLoggerFactory.getLogger(LogEnum.API.name());

    public Web3EntryPoint(PersonalRPC personal, List<String> enabledGroup, List<String> enabledMethods, List<String> disabledMethods){
        this.enabledMethods = Set.copyOf(enabledMethods);
        this.disabledMethods = Set.copyOf(disabledMethods);
        this.executablePredicateMaps = Map.ofEntries(Map.entry("personal", personal::isExecutable));
        Map<String, Function<Request, String>> temp = new HashMap<>();
        temp.put("personal", personal::execute);
        if (enabledGroup != null) {
            for (String s: temp.keySet()){
                if (!enabledGroup.contains(s)){
                    temp.remove(s);
                }
            }
        }
        groupMap= Collections.unmodifiableMap(temp);
    }

    public String call(String requestString){
        logger.debug("Received request: {}",requestString);
        Request request;
        try{
            request = RequestConverter.decode(requestString);
            String group = request.method.split("_")[0];
            if (groupMap.containsKey(group) &&
                checkMethod(request.method)){
                return groupMap.get(group).apply(request);
            }else {
                return new RPCExceptions.InvalidRequestRPCException().getMessage();
            }
        }catch (Exception e){
            return new RPCExceptions.InternalErrorRPCException().getMessage();
        }
    }

    public boolean isExecutable(String method){
        String group = method.split("_")[0];
        return executablePredicateMaps.containsKey(group) && executablePredicateMaps.get(group).test(method);
    }

    public boolean checkMethod(String method){
       if (enabledMethods !=null && enabledMethods.contains(method)){
           return true;
       }
       else
           return disabledMethods == null || !disabledMethods.contains(method);
    }
}
