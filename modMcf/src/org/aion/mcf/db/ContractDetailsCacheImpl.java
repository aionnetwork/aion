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

import static java.util.Collections.unmodifiableMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.db.IContractDetails;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.DataWord;
import org.aion.rlp.RLP;
import org.aion.mcf.trie.SecureTrie;

/**
 * Contract details cache implementation.
 */
public class ContractDetailsCacheImpl extends AbstractContractDetails<DataWord> {

    private Map<DataWord, DataWord> storage = new HashMap<>();

    public IContractDetails<DataWord> origContract;

    public ContractDetailsCacheImpl(IContractDetails<DataWord> origContract) {
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
    public void put(DataWord key, DataWord value) {
        storage.put(key, value);
        this.setDirty(true);
    }

    @Override
    public DataWord get(DataWord key) {

        DataWord value = storage.get(key);
        if (value != null) {
            value = value.clone();
        } else {
            if (origContract == null) {
                return null;
            }
            value = origContract.get(key);
            storage.put(key.clone(), value == null ? DataWord.ZERO.clone() : value.clone());
        }

        if (value == null || value.isZero()) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public byte[] getStorageHash() { // todo: unsupported

        SecureTrie storageTrie = new SecureTrie(null);

        for (DataWord key : storage.keySet()) {

            DataWord value = storage.get(key);

            storageTrie.update(key.getData(), RLP.encodeElement(value.getNoLeadZeroesData()));
        }

        return storageTrie.getRootHash();
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
    public Map<DataWord, DataWord> getStorage() {
        return unmodifiableMap(storage);
    }

    @Override
    public Map<DataWord, DataWord> getStorage(Collection<DataWord> keys) {
        if (keys == null) {
            return getStorage();
        }

        Map<DataWord, DataWord> result = new HashMap<>();
        for (DataWord key : keys) {
            result.put(key, storage.get(key));
        }
        return unmodifiableMap(result);
    }

    @Override
    public int getStorageSize() {
        return (origContract == null) ? storage.size() : origContract.getStorageSize();
    }

    @Override
    public Set<DataWord> getStorageKeys() {
        return (origContract == null) ? storage.keySet() : origContract.getStorageKeys();
    }

    @Override
    public void setStorage(List<DataWord> storageKeys, List<DataWord> storageValues) {

        for (int i = 0; i < storageKeys.size(); ++i) {

            DataWord key = storageKeys.get(i);
            DataWord value = storageValues.get(i);

            if (value.isZero()) {
                storage.put(key, null);
            }
        }

    }

    @Override
    public void setStorage(Map<DataWord, DataWord> storage) {
        this.storage = storage;
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
    public IContractDetails<DataWord> clone() {

        ContractDetailsCacheImpl contractDetails = new ContractDetailsCacheImpl(origContract);

        Object storageClone = ((HashMap<DataWord, DataWord>) storage).clone();

        contractDetails.setCode(this.getCode());
        contractDetails.setStorage((HashMap<DataWord, DataWord>) storageClone);
        return contractDetails;
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

        for (DataWord key : storage.keySet()) {
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
    public IContractDetails<DataWord> getSnapshotTo(byte[] hash) {
        throw new UnsupportedOperationException("No snapshot option during cache state");
    }

    @Override
    public void setDataSource(IByteArrayKeyValueStore dataSource) {
        throw new UnsupportedOperationException("Can't set datasource in cache implementation.");
    }
}
