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
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl.zero.msg;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author chris
 *
 * 2018-03-07 extends versions, revision
 * protocal version upgrading test
 *
 */
public final class ReqHandshake1 extends ReqHandshake {

    private byte[] revision;

    private List<Short> versions;

    // one version byte[2] - short
    private static final byte MAX_VERSIONS_LEN = 63;

    // super LEN + revision len (byte) + versions len (byte)
    private static final int MIN_LEN = LEN + 2;

    /**
     *
     * @param _nodeId byte[36]
     * @param _netId int
     * @param _ip byte[8]
     * @param _port int
     * @param _revision String
     * @param _versions List<byte[2]> header contains 2 byte version
     */
    public ReqHandshake1(final byte[] _nodeId, int _netId, final byte[] _ip, int _port, final byte[] _revision, final List<Short> _versions) {
        super(_nodeId, _netId, _ip, _port);
        this.revision = _revision;
        this.versions = _versions.subList(0, Math.min(MAX_VERSIONS_LEN, _versions.size()));
    }

    public byte[] getRevision(){
        return this.revision;
    }

    /**
     * @param _bytes byte[]
     * @return ReqHandshake
     * decode body
     */
    public static ReqHandshake1 decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length < MIN_LEN)
            return null;
        else {
            ByteBuffer buf = ByteBuffer.wrap(_bytes);

            // decode node id
            byte[] nodeId = new byte[36];
            buf.get(nodeId);

            // decode net id
            int netId = buf.getInt();

            // decode ip
            byte[] ip = new byte[8];
            buf.get(ip);

            // decode port
            int port = buf.getInt();

            // decode revision
            byte revisionLen = buf.get();
            byte[] revision = new byte[revisionLen];
            buf.get(revision);

            // decode versions
            byte versionsLen = buf.get();
            List<Short> versions = new ArrayList<>();
            for(byte i = 0; i < versionsLen; i++){
                short version = buf.getShort();
                versions.add(version);
            }

            return new ReqHandshake1(nodeId, netId, ip, port, revision, versions);
        }
    }

    @Override
    public byte[] encode() {
        if (this.nodeId.length != 36)
            return null;
        else {
            byte[] superBytes = super.encode();
            if(superBytes == null)
                return null;
            byte revisionLen = (byte)this.revision.length;
            byte versionsLen = (byte)this.versions.size();
            ByteBuffer buf = ByteBuffer.allocate(superBytes.length + 1 + revisionLen + 1 + versionsLen * 2);
            buf.put(superBytes);
            buf.put(revisionLen);
            buf.put(this.revision);
            buf.put(versionsLen);
            for(Short version : versions){
                buf.putShort(version);
            }
            return buf.array();
        }
    }

}
