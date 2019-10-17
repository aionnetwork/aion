package org.aion.api.server.rpc3;

import static org.aion.api.server.rpc3.RPCExceptions.*;
import static org.aion.api.server.rpc3.types.RPCTypes.*;
import static org.aion.api.server.rpc3.types.RPCTypesConverter.*;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.types.AionAddress;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public abstract class PersonalRPC{

    /**
     *Allows you to interact with accounts on the aion network and provides a handful of crypto utilities
     */
    public String execute(String requestString){
        try{

            Request request = RequestConverter.decode(requestString);
            //check that the request can be fulfilled by this class
            String interfaceName = request.method.split("_")[0];
            if(interfaceName.equals("personal")){
                if(request.method.equals("personal_ecRecover")){
                    EcRecoverParams params=EcRecoverParamsConverter.decode(request.params);

                    ByteArrayWrapper result = personal_ecRecover(params.dataThatWasSigned,params.signature);
                    return DataHexStringConverter.encode(result);
                }
                else
                    throw new MethodNotFoundRPCException();
            }
            else{
                throw new InternalErrorRPCException();
            }
        }
        catch(InvalidRequestRPCException |ParseErrorRPCException |MethodNotFoundRPCException |InvalidParamsRPCException |InternalErrorRPCException e){
            return e.getMessage();
        }
    }

    abstract ByteArrayWrapper personal_ecRecover(ByteArrayWrapper dataThatWasSigned,ByteArrayWrapper signature);
}
