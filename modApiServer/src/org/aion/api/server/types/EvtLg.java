/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import org.aion.api.server.types.Fltr.Type;
import org.aion.base.util.TypeConverter;
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