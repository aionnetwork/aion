package org.aion.api.server.types;

import org.aion.api.server.types.Fltr.Type;
import org.json.JSONArray;
import org.json.JSONObject;

public class EvtLg extends Evt {

    private final TxRecptLg el;

    public EvtLg(TxRecptLg el) {
        this.el = el;
    }

    @Override
    public Type getType() {
        return Type.LOG;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();

        obj.put("removed", this.el.removed);
        obj.put("logIndex", this.el.logIndex);
        obj.put("transactionIndex", this.el.transactionIndex);
        obj.put("transactionHash", this.el.transactionHash);
        obj.put("blockHash", this.el.blockHash);
        obj.put("blockNumber", this.el.blockNumber);
        obj.put("address", this.el.address);
        obj.put("data", this.el.data);
        obj.put("topics", new JSONArray(this.el.topics));

        return obj;
    }
}
