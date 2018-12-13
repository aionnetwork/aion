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
package org.aion.mcf.types;

import java.util.Arrays;
import org.aion.base.type.IBlockHeader;
import org.aion.rlp.RLP;
import org.aion.util.conversions.Hex;

/** AbstractBlockHeaderWrapper */
public abstract class AbstractBlockHeaderWrapper<BH extends IBlockHeader> {

    protected BH header;

    protected byte[] nodeId;

    public AbstractBlockHeaderWrapper() {}

    public AbstractBlockHeaderWrapper(BH header, byte[] nodeId) {
        this.header = header;
        this.nodeId = nodeId;
    }

    public AbstractBlockHeaderWrapper(byte[] bytes) {
        parse(bytes);
    }

    public byte[] getBytes() {
        byte[] headerBytes = header.getEncoded();
        byte[] nodeIdBytes = RLP.encodeElement(nodeId);
        return RLP.encodeList(headerBytes, nodeIdBytes);
    }

    protected abstract void parse(byte[] bytes);

    public byte[] getNodeId() {
        return nodeId;
    }

    public byte[] getHash() {
        return header.getHash();
    }

    public long getNumber() {
        return header.getNumber();
    }

    public BH getHeader() {
        return header;
    }

    public String getHexStrShort() {
        return Hex.toHexString(header.getHash()).substring(0, 6);
    }

    public boolean sentBy(byte[] nodeId) {
        return Arrays.equals(this.nodeId, nodeId);
    }

    @Override
    public String toString() {
        return "BlockHeaderWrapper {"
                + "header="
                + header
                + ", nodeId="
                + Hex.toHexString(nodeId)
                + '}';
    }
}
