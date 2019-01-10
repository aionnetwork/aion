package org.aion.api.server.types;

import org.json.JSONException;
import org.json.JSONObject;

public class CompiledContr {

    public String error;

    public String code;

    public CompiContrInfo info;

    /** For RPC call */
    public JSONObject toJSON() {
        JSONObject jsonObj = new JSONObject();
        try {
            if (error != null) jsonObj.put("error", error);
            else {
                jsonObj.put("code", code);
                jsonObj.put("info", info.toJSON());
            }
        } catch (JSONException ex) {
        }
        return jsonObj;
    }
}
