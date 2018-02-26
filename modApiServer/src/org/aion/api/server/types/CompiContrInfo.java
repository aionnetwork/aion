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
    
    /**
     * For RPC call
     */
    public JSONObject toJSON() {
        JSONObject res = new JSONObject();
        res.put("source", this.source);
        res.put("language", this.language);
        res.put("languageVersion", this.languageVersion);
        res.put("compilerVersion", this.compilerVersion);
        res.put("userDoc", this.userDoc);
        res.put("developerDoc", this.developerDoc);
        JSONArray arr = new JSONArray();
        for(int i = 0, m = this.abiDefinition.length; i < m; i++) {
            arr.put(this.abiDefinition[i].toJSON());
        }
        res.put("abiDefinition", arr);
        return res;
    }

}
