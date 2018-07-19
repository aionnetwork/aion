/* ******************************************************************************
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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.mcf.ds;

import java.util.*;
import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;

public class XorDataSource implements IByteArrayKeyValueStore {
    IByteArrayKeyValueStore source;
    byte[] subKey;

    public XorDataSource(IByteArrayKeyValueStore source, byte[] subKey) {
        this.source = source;
        this.subKey = subKey;
    }

    private byte[] convertKey(byte[] key) {
        return ByteUtil.xorAlignRight(key, subKey);
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        return source.get(convertKey(key));
    }

    @Override
    public void put(byte[] key, byte[] value) {
        source.put(convertKey(key), value);
    }

    @Override
    public void delete(byte[] key) {
        source.delete(convertKey(key));
    }

    @Override
    public Set<byte[]> keys() {
        Set<byte[]> keys = source.keys();
        Set<byte[]> ret = new HashSet<>(keys.size());
        for (byte[] key : keys) {
            ret.add(convertKey(key));
        }
        return ret;
    }

    @Override
    public void putBatch(Map<byte[], byte[]> rows) {
        Map<byte[], byte[]> converted = new HashMap<>(rows.size());
        for (Map.Entry<byte[], byte[]> entry : rows.entrySet()) {
            converted.put(convertKey(entry.getKey()), entry.getValue());
        }
        source.putBatch(converted);
    }

    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, boolean erasure) {
        // not supported
        throw new UnsupportedOperationException(
                "ByteArrayWrapper map not supported in XorDataSource.updateBatch yet");
    }

    @Override
    public void close() throws Exception {
        source.close();
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        // TODO Auto-generated method stub

    }

    @Override
    public void check() {
        source.check();
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commitBatch() {
        throw new UnsupportedOperationException();
    }
}
