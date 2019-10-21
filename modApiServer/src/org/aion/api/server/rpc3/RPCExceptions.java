package org.aion.api.server.rpc3;

import org.aion.api.server.rpc3.types.RPCTypes.Error;
import org.aion.api.server.rpc3.types.RPCTypesConverter.ErrorConverter;
/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public class RPCExceptions{
    public static RPCException fromCode(int code){
        if(code == -32600){
            return InvalidRequestRPCException.INSTANCE;
        }
        else if(code == -32700){
            return ParseErrorRPCException.INSTANCE;
        }
        else if(code == -32601){
            return MethodNotFoundRPCException.INSTANCE;
        }
        else if(code == -32602){
            return InvalidParamsRPCException.INSTANCE;
        }
        else if(code == -32603){
            return InternalErrorRPCException.INSTANCE;
        }
        else 
            return InternalErrorRPCException.INSTANCE;
    }

    public abstract static class RPCException extends RuntimeException{

        private Error error;
        protected RPCException(String message){
            super(message);
            this.error = ErrorConverter.decode(message);
        }
        public Error getError(){
            return error;
        }
    }

    public static class InvalidRequestRPCException extends RPCException{
        public static final InvalidRequestRPCException INSTANCE = new InvalidRequestRPCException();
        private InvalidRequestRPCException(){
            super("{\"code\":-32600,\"message\":\"Invalid Request\"}");
        }
    }

    public static class ParseErrorRPCException extends RPCException{
        public static final ParseErrorRPCException INSTANCE = new ParseErrorRPCException();
        private ParseErrorRPCException(){
            super("{\"code\":-32700,\"message\":\"Parse error\"}");
        }
    }

    public static class MethodNotFoundRPCException extends RPCException{
        public static final MethodNotFoundRPCException INSTANCE = new MethodNotFoundRPCException();
        private MethodNotFoundRPCException(){
            super("{\"code\":-32601,\"message\":\"Method not found\"}");
        }
    }

    public static class InvalidParamsRPCException extends RPCException{
        public static final InvalidParamsRPCException INSTANCE = new InvalidParamsRPCException();
        private InvalidParamsRPCException(){
            super("{\"code\":-32602,\"message\":\"Invalid params\"}");
        }
    }

    public static class InternalErrorRPCException extends RPCException{
        public static final InternalErrorRPCException INSTANCE = new InternalErrorRPCException();
        private InternalErrorRPCException(){
            super("{\"code\":-32603,\"message\":\"Internal error\"}");
        }
    }

}
