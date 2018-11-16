/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.api.server.rpc;

/**
 * @author ali sharif
 *     <p>Json rpc error object http://www.jsonrpc.org/specification#error_object
 *     <p>Further support for rpc error code EIP
 *     https://github.com/ethereum/wiki/wiki/JSON-RPC-Error-Codes-Improvement-Proposal
 *     <p>There are various errors you can get back from the API. Here we will detail what they
 *     mean, and sometimes how to handle them.
 *     <p>RPC Error Types: 1. Parse error 2. Invalid request 3. Method not found 4. Invalid params
 *     5. Internal error
 *     <p>1. Parse Error --------------
 *     <p>What does this mean? The server had an error parsing the JSON
 *     <p>What to do? Check the JSON you provided and ensure it is valid.
 *     <p>2. Invalid Request ------------------
 *     <p>What does this mean? The JSON sent is not a valid Request object.
 *     <p>What to do? Ensure that your request matches our JSON-RPC standard.
 *     <p>3. Method not found -------------------
 *     <p>What does this mean? If you provide a method that is not a supported API method, this
 *     error will be returned.
 *     <p>What to do? Check the method you provided for a typo.
 *     <p>4. Invalid params -----------------
 *     <p>What does this mean? The params sent do not match the expected parameters
 *     <p>What to do? Ensure that your parameters are correct for the API endpoint you are trying to
 *     call. Also, ensure that you have all the required fields.
 *     <p>5. Internal error -----------------
 *     <p>What does this mean? There was an internal error when processing your request.
 *     <p>What to do? This error is hard to fix from the clientside, many times it is related to
 *     database lookups.
 */
public enum RpcError {

    // rpc spec error codes
    PARSE_ERROR(-32700, "Parse error"),
    INVALID_REQUEST(-32600, "Invalid request"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INTERNAL_ERROR(-32603, "Internal error"),
    SERVER_OVERLOAD(-32005, "Server under load; worker queue full"),

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
