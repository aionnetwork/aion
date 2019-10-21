package org.aion.api.server.rpc3;

import static org.aion.api.server.rpc3.RPCExceptions.*;
import static org.aion.api.server.rpc3.types.RPCTypes.*;
import static org.aion.api.server.rpc3.types.RPCTypesConverter.*;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.types.AionAddress;
import java.util.Set;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public interface PersonalRPC{

    default Object execute(Request request){
        Object res;
        try{
            //check that the request can be fulfilled by this class
            if(request.method.equals("personal_ecRecover")){
                EcRecoverParams params;
                try{
                    params=EcRecoverParamsConverter.decode(request.params);
                }catch(Exception e){
                    throw InvalidParamsRPCException.INSTANCE;
                }
                AionAddress result = this.personal_ecRecover(params.dataThatWasSigned,params.signature);
                res = AionAddressConverter.encode(result);
            }else
                throw MethodNotFoundRPCException.INSTANCE;
        }
        catch(InvalidRequestRPCException |ParseErrorRPCException |MethodNotFoundRPCException |InvalidParamsRPCException |InternalErrorRPCException e){
            throw e;
        }
        catch(Exception e){
            throw InternalErrorRPCException.INSTANCE;
        }
        return res;
    }

    boolean isExecutable(String method);

    default Set<String> listMethods(){
        return Set.of( "personal_ecRecover");
    }

    AionAddress personal_ecRecover(ByteArrayWrapper dataThatWasSigned, ByteArrayWrapper signature);
}
