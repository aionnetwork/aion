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
 ******************************************************************************/
package org.aion.mcf.ds;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IByteArrayKeyValueStore;

/**
 * A data source with archived data that must no be deleted.
 *
 * @author Alexandra Roatis
 */
public class ArchivedDataSource implements IByteArrayKeyValueStore {

    IByteArrayKeyValueDatabase data, archive;

    public ArchivedDataSource(IByteArrayKeyValueDatabase _db, IByteArrayKeyValueDatabase _archive) {
        this.data = _db;
        this.archive = _archive;
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public Set<byte[]> keys() {
        return data.keys();
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        return data.get(key);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        if (value != null) {
            data.put(key, value);
        } else {
            // internal delete will check if archived
            delete(key);
        }
    }

    @Override
    public void delete(byte[] key) {
        // delete key only if not archived
        if (!archive.get(key).isPresent()) {
            data.delete(key);
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> batch) {
        for (Map.Entry<byte[], byte[]> entry : batch.entrySet()) {
            // will check if archived
            putToBatch(entry.getKey(), entry.getValue());
        }
        commitBatch();
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        if (value != null) {
            data.putToBatch(key, value);
        } else {
            // deleted key only if not archived
            if (!archive.get(key).isPresent()) {
                data.putToBatch(key, null);
            }
        }
    }

    @Override
    public void commitBatch() {
        data.commitBatch();
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        for (byte[] key : keys) {
            // will check if archived
            putToBatch(key, null);
        }
        commitBatch();
    }

    @Override
    public void check() {
        data.check();
        archive.check();
    }

    @Override
    public void close() {
        data.close();
        archive.close();
    }

    public IByteArrayKeyValueDatabase getArchiveDatabase() {
        return archive;
    }
}
