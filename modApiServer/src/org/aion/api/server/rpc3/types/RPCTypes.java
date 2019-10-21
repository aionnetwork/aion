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
    public static final class Request {
        public final Integer id;
        public final String method;
        public final String params;
        public final VersionType jsonrpc;

        public Request(Integer id ,String method ,String params ,VersionType jsonrpc ){
            this.id=id;
            if(method==null) throw ParseErrorRPCException.INSTANCE;
            this.method=method;
            if(params==null) throw ParseErrorRPCException.INSTANCE;
            this.params=params;
            this.jsonrpc=jsonrpc;
        }
    }
    /**
    * This is the standard response body for a JSON RPC Request
    */
    public static final class Response {
        public final Integer id;
        public final Object result;
        public final Error error;
        public final VersionType jsonrpc;

        public Response(Integer id ,Object result ,Error error ,VersionType jsonrpc ){
            this.id=id;
            this.result=result;
            this.error=error;
            if(jsonrpc==null) throw ParseErrorRPCException.INSTANCE;
            this.jsonrpc=jsonrpc;
        }
    }
    /**
    * Contains the error messages for failed JSON RPC Requests
    */
    public static final class Error {
        public final Integer code;
        public final String message;

        public Error(Integer code ,String message ){
            if(code==null) throw ParseErrorRPCException.INSTANCE;
            this.code=code;
            if(message==null) throw ParseErrorRPCException.INSTANCE;
            this.message=message;
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
                throw ParseErrorRPCException.INSTANCE;
        }
    }

    public static final class EcRecoverParams {
        public final ByteArrayWrapper dataThatWasSigned;
        public final ByteArrayWrapper signature;

        public EcRecoverParams(ByteArrayWrapper dataThatWasSigned ,ByteArrayWrapper signature ){
            if(dataThatWasSigned==null) throw ParseErrorRPCException.INSTANCE;
            this.dataThatWasSigned=dataThatWasSigned;
            if(signature==null) throw ParseErrorRPCException.INSTANCE;
            this.signature=signature;
        }
    }
}
