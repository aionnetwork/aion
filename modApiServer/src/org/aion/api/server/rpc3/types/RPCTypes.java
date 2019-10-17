package org.aion.api.server.rpc3.types;

import org.aion.api.server.rpc3.RPCExceptions.ParseErrorRPCException;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.types.AionAddress;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public class RPCTypes{
    /**
    * This is the standard request body for a JSON RPC Request
    */
    public static class Request {
        public final Integer id;
        public final String method;
        public final String params;
        public final VersionType jsonRPC;

        public Request(Integer id ,String method ,String params ,VersionType jsonRPC ){
            if(id==null) throw new ParseErrorRPCException();
            this.id=id;
            if(method==null) throw new ParseErrorRPCException();
            this.method=method;
            if(params==null) throw new ParseErrorRPCException();
            this.params=params;
            this.jsonRPC=jsonRPC;
        }
    }

    public enum VersionType{
        Version2("2.0");
        public final String x;
        VersionType(String x){
            this.x = x;
        }

        public static VersionType fromString(String x){
            if(x.equals("2.0")){
                return Version2;
            }else
                throw new ParseErrorRPCException();
        }
    }

    public static class EcRecoverParams {
        public final String dataThatWasSigned;
        public final ByteArrayWrapper signature;

        public EcRecoverParams(String dataThatWasSigned ,ByteArrayWrapper signature ){
            if(dataThatWasSigned==null) throw new ParseErrorRPCException();
            this.dataThatWasSigned=dataThatWasSigned;
            if(signature==null) throw new ParseErrorRPCException();
            this.signature=signature;
        }
    }
}
