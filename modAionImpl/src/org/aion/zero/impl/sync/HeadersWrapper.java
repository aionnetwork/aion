/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 */

package org.aion.zero.impl.sync;

import org.aion.zero.types.A0BlockHeader;
import java.util.List;

/**
 * @author chris
 * used by imported headers on sync mgr
 */
final class HeadersWrapper {

    private int nodeIdHash;

    private String displayId;

    private long timestamp;

    private List<A0BlockHeader> headers;

    /**
     *
     * @param _nodeIdHash int
     * @param _headers List
     */
    HeadersWrapper(int _nodeIdHash, String _displayId, final List<A0BlockHeader> _headers){
        this.nodeIdHash = _nodeIdHash;
        this.displayId = _displayId;
        this.headers = _headers;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return int - node id hash
     */
    int getNodeIdHash(){
        return this.nodeIdHash;
    }

    /**
     * @return String - node display id
     */
    String getDisplayId() { return this.displayId; }

    /**
     * @return long
     * used to compare and drop from queue if expired
     */
    long getTimestamp(){
        return this.timestamp;
    }

    /**
     * @return List
     */
    List<A0BlockHeader> getHeaders(){
        return this.headers;
    }

}
