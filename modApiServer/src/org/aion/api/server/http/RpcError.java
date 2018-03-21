package org.aion.api.server.http;

/**
 * @author ali sharif
 *
 * Json rpc error object
 * http://www.jsonrpc.org/specification#error_object
 *
 * Further support for rpc error code EIP
 * https://github.com/ethereum/wiki/wiki/JSON-RPC-Error-Codes-Improvement-Proposal
 *
 * There are various errors you can get back from the API. Here we will detail what
 * they mean, and sometimes how to handle them.
 *
 * RPC Error Types:
 *   1. Parse error
 *   2. Invalid request
 *   3. Method not found
 *   4. Invalid params
 *   5. Internal error
 *
 * 1. Parse Error
 * --------------
 *
 * What does this mean?
 * The server had an error parsing the JSON
 *
 * What to do?
 * Check the JSON you provided and ensure it is valid.
 *
 * 2. Invalid Request
 * ------------------
 *
 * What does this mean?
 * The JSON sent is not a valid Request object.
 *
 * What to do?
 * Ensure that your request matches our JSON-RPC standard.
 *
 * 3. Method not found
 * -------------------
 *
 * What does this mean?
 * If you provide a method that is not a supported API method, this error will be returned.
 *
 * What to do?
 * Check the method you provided for a typo.
 *
 * 4. Invalid params
 * -----------------
 *
 * What does this mean?
 * The params sent do not match the expected parameters
 *
 * What to do?
 * Ensure that your parameters are correct for the API endpoint you are trying to call. Also, ensure that you have all the required fields.
 *
 * 5. Internal error
 * -----------------
 *
 * What does this mean?
 * There was an internal error when processing your request.
 *
 * What to do?
 * This error is hard to fix from the clientside, many times it is related to database lookups.
 *
 */
public enum RpcError {

    // rpc spec error codes
    PARSE_ERROR(-32700, "Parse error"),
    INVALID_REQUEST(-32600, "Invalid request"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INTERNAL_ERROR(-32603, "Internal error"),

    // custom error codes
    UNAUTHORIZED(1, "Unauthorized"),
    NOT_ALLOWED(2, "Action not allowed"),
    EXECUTION_ERROR(3, "Execution error");

    private final int code;
    private final String message;

    private RpcError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }
}
