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
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/
package org.aion.mcf.db;

import org.aion.base.db.IContractDetails;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.Hex;

import java.util.HashMap;
import java.util.Map;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;

/**
 * Abstract contract details.
 */
public abstract class AbstractContractDetails<DW> implements IContractDetails<DW> {

    private boolean dirty = false;
    private boolean deleted = false;

    protected int prune;
    protected int detailsInMemoryStorageLimit;

    private Map<ByteArrayWrapper, byte[]> codes = new HashMap<>();

    protected AbstractContractDetails() {
        this(0, 64 * 1024);
    }

    protected AbstractContractDetails(int prune, int memStorageLimit) {
        this.prune = prune;
        this.detailsInMemoryStorageLimit = memStorageLimit;
    }

    @Override
    public byte[] getCode() {
        return codes.size() == 0 ? EMPTY_BYTE_ARRAY : codes.values().iterator().next();
    }

    @Override
    public byte[] getCode(byte[] codeHash) {
        if (java.util.Arrays.equals(codeHash, EMPTY_DATA_HASH)) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] code = codes.get(new ByteArrayWrapper(codeHash));
        return code == null ? EMPTY_BYTE_ARRAY : code;
    }

    @Override
    public void setCode(byte[] code) {
        if (code == null) {
            return;
        }
        try {
            codes.put(ByteArrayWrapper.wrap(h256(code)), code);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        setDirty(true);
    }

    public Map<ByteArrayWrapper, byte[]> getCodes() {
        return codes;
    }

    protected void setCodes(Map<ByteArrayWrapper, byte[]> codes) {
        this.codes = new HashMap<>(codes);
    }

    public void appendCodes(Map<ByteArrayWrapper, byte[]> codes) {
        this.codes.putAll(codes);
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public String toString() {
        String ret = "  Code: " + (codes.size() < 2 ? Hex.toHexString(getCode()) : codes.size() + " versions") + "\n";
        ret += "  Storage: " + getStorage().toString();
        return ret;
    }
}
