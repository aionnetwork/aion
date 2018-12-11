package org.aion.api.server.types;

import org.aion.solidity.Abi.Entry;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CompiContrInfo {

    public String source;

    public String language;

    public String languageVersion;

    public String compilerVersion;

    public String userDoc;

    public String developerDoc;

    public Entry[] abiDefinition;

    /** For RPC call */
    public JSONObject toJSON() {
        JSONObject res = new JSONObject();
        res.put("source", this.source);
        res.put("language", this.language);
        res.put("languageVersion", this.languageVersion);
        res.put("compilerVersion", this.compilerVersion);
        res.put("userDoc", this.userDoc);
        res.put("developerDoc", this.developerDoc);
        JSONArray arr = new JSONArray();
        for (int i = 0, m = this.abiDefinition.length; i < m; i++) {
            arr.put(this.abiDefinition[i].toJSON());
        }
        res.put("abiDefinition", arr);
        return res;
    }
}
