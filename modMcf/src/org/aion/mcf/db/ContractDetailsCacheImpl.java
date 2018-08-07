/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/
package org.aion.mcf.db;

import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.db.IContractDetails;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.vm.types.DataWord;

import java.util.*;

/**
 * Contract details cache implementation.
 */
public class ContractDetailsCacheImpl extends AbstractContractDetails<IDataWord> {

    private Map<IDataWord, IDataWord> storage = new HashMap<>();

    public IContractDetails<IDataWord> origContract;

    public ContractDetailsCacheImpl(IContractDetails<IDataWord> origContract) {
        this.origContract = origContract;
        if (origContract != null) {
            if (origContract instanceof AbstractContractDetails) {
                setCodes(((AbstractContractDetails) this.origContract).getCodes());
            } else {
                setCode(origContract.getCode());
            }
        }
    }

    @Override
    public void put(IDataWord key, IDataWord value) {
        storage.put(key, value);
        setDirty(true);
    }

    @Override
    public IDataWord get(IDataWord key) {

        IDataWord value = storage.get(key);
        if (value != null) {
            value = value.copy();
        } else {
            if (origContract == null) {
                return null;
            }
            value = origContract.get(key);
            storage.put(key.copy(), value == null ? DataWord.ZERO : value.copy());
        }

        if (value == null || value.isZero()) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public byte[] getStorageHash() {
        return origContract.getStorageHash();
    }

    @Override
    public void decode(byte[] rlpCode) {
        throw new RuntimeException("Not supported by this implementation.");
    }

    @Override
    public byte[] getEncoded() {
        throw new RuntimeException("Not supported by this implementation.");
    }

    @Override
    public Map<IDataWord, IDataWord> getStorage(Collection<IDataWord> keys) {
        Map<IDataWord, IDataWord> storage = new HashMap<>();
        if (keys == null) {
            throw new IllegalArgumentException("Input keys can't be null");
        } else {
            for (IDataWord key : keys) {
                IDataWord value = get(key);

                // we check if the value is not null,
                // cause we keep all historical keys
                if (value != null) {
                    storage.put(key, value);
                }
            }
        }

        return storage;
    }

    @Override
    public void setStorage(List<IDataWord> storageKeys, List<IDataWord> storageValues) {

        for (int i = 0; i < storageKeys.size(); ++i) {

            IDataWord key = storageKeys.get(i);
            IDataWord value = storageValues.get(i);

            put(key, value);
        }

    }

    @Override
    public void setStorage(Map<IDataWord, IDataWord> storage) {
        for (Map.Entry<IDataWord, IDataWord> entry : storage.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Address getAddress() {
        return (origContract == null) ? null : origContract.getAddress();
    }

    @Override
    public void setAddress(Address address) {
        if (origContract != null) {
            origContract.setAddress(address);
        }
    }

    @Override
    public void syncStorage() {
        if (origContract != null) {
            origContract.syncStorage();
        }
    }

    public void commit() {

        if (origContract == null) {
            return;
        }

        for (IDataWord key : storage.keySet()) {
            origContract.put(key, storage.get(key));
        }

        if (origContract instanceof AbstractContractDetails) {
            ((AbstractContractDetails) origContract).appendCodes(getCodes());
        } else {
            origContract.setCode(getCode());
        }
        origContract.setDirty(this.isDirty() || origContract.isDirty());
    }

    @Override
    public IContractDetails<IDataWord> getSnapshotTo(byte[] hash) {
        throw new UnsupportedOperationException("No snapshot option during cache state");
    }

    @Override
    public void setDataSource(IByteArrayKeyValueStore dataSource) {
        throw new UnsupportedOperationException("Can't set datasource in cache implementation.");
    }
}
