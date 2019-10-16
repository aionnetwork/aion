package org.aion.api.server.rpc3;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public class RPCExceptions{
    public static class InvalidRequestRPCException extends RuntimeException{
        public InvalidRequestRPCException(){
            super("{code:-32600,message:'Invalid Request'}");
        }
    }

    public static class ParseErrorRPCException extends RuntimeException{
        public ParseErrorRPCException(){
            super("{code:-32700,message:'Parse error'}");
        }
    }

    public static class MethodNotFoundRPCException extends RuntimeException{
        public MethodNotFoundRPCException(){
            super("{code:-32601,message:'Method not found'}");
        }
    }

    public static class InvalidParamsRPCException extends RuntimeException{
        public InvalidParamsRPCException(){
            super("{code:-32602,message:'Invalid params'}");
        }
    }

    public static class InternalErrorRPCException extends RuntimeException{
        public InternalErrorRPCException(){
            super("{code:-32603,message:'Internal error'}");
        }
    }

}