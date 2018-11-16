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

import org.json.JSONObject;

/**
 * @author ali sharif
 *     <p>Simple data structure to pass rpc messages
 */
public class RpcMsg {
    private Object result;
    private RpcError error;
    private Object errorData;
    private Object id;

    public RpcMsg(Object result) {
        this(result, null, null);
    }

    public RpcMsg(Object result, RpcError error) {
        this(result, error, null);
    }

    public RpcMsg(Object result, RpcError error, Object errorData) {
        this.result = result;
        this.error = error;
        this.errorData = errorData;
        this.id = JSONObject.NULL;
    }

    public Object getResult() {
        return result;
    }

    public Object getError() {
        return error;
    }

    public RpcMsg setId(Object id) {
        this.id = id;
        return this;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("jsonrpc", "2.0");
        json.put("id", this.id);

        // according to the json-rpc spec (http://www.jsonrpc.org/specification):
        // error: This member is REQUIRED on error. This member MUST NOT exist if there was no error
        // triggered during invocation.
        // result: This member is REQUIRED on success. This member MUST NOT exist if there was an
        // error invoking the method.
        if (this.result == null) { // call equals on the leaf type
            RpcError e = this.error;
            if (e == null) e = RpcError.INTERNAL_ERROR;

            JSONObject error = new JSONObject();
            error.put("code", e.getCode());
            error.put("message", e.getMessage());
            error.put("data", this.errorData);
            json.put("error", error);
        } else {
            json.put("result", this.result);
        }
        return json;
    }

    @Override
    public String toString() {
        return this.toJson().toString();
    }
}
