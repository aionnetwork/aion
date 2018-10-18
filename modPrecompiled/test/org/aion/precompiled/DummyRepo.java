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
package org.aion.precompiled;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;

public class DummyRepo implements IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> {
    private Map<Address, AccountState> accounts = new HashMap<>();
    private Map<Address, byte[]> contracts = new HashMap<>();
    private Map<Address, Map<String, byte[]>> storage = new HashMap<>();

    // Made this alterable for testing since this default value is not always what real implementations
    // do ... and don't want to break tests that rely on this value.
    public IDataWord storageErrorReturn = DoubleDataWord.ZERO;

    public DummyRepo() {}

    public DummyRepo(DummyRepo parent) {
        // Note: only references are copied
        accounts.putAll(parent.accounts);
        contracts.putAll(parent.contracts);
        storage.putAll(parent.storage);
    }

    void addContract(Address address, byte[] code) {
        contracts.put(address, code);
    }

    @Override
    public AccountState createAccount(Address addr) {
        AccountState as = new AccountState();
        accounts.put(addr, as);
        return as;
    }

    @Override
    public boolean hasAccountState(Address addr) {
        return accounts.containsKey(addr);
    }

    @Override
    public AccountState getAccountState(Address addr) {
        if (!hasAccountState(addr)) {
            createAccount(addr);
        }
        return accounts.get(addr);
    }

    @Override
    public void deleteAccount(Address addr) {
        accounts.remove(addr);
    }

    // Throws exception if account does not exist.
    @Override
    public BigInteger incrementNonce(Address addr) {
        AccountState as = getAccountState(addr);
        as.incrementNonce();
        return as.getNonce();
    }

    @Override
    public BigInteger setNonce(Address address, BigInteger nonce) {
        throw new RuntimeException("Not supported");
    }

    // an exception will be thrown if account does not exist
    @Override
    public BigInteger getNonce(Address addr) {
        return getAccountState(addr).getNonce();
    }

    @Override
    public IContractDetails<IDataWord> getContractDetails(Address addr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasContractDetails(Address addr) {
        return contracts.containsKey(addr);
    }

    @Override
    public void saveCode(Address addr, byte[] code) {
        contracts.put(addr, code);
    }

    @Override
    public byte[] getCode(Address addr) {
        byte[] code = contracts.get(addr);
        return code == null ? ByteUtil.EMPTY_BYTE_ARRAY : code;
    }

    @Override
    public Map<IDataWord, IDataWord> getStorage(Address address, Collection<IDataWord> keys) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void addStorageRow(Address addr, IDataWord key, IDataWord value) {
        Map<String, byte[]> map = storage.computeIfAbsent(addr, k -> new HashMap<>());
        map.put(key.toString(), value.getData());
    }

    @Override
    public IDataWord getStorageValue(Address addr, IDataWord key) {
        Map<String, byte[]> map = storage.get(addr);
        if (map != null && map.containsKey(key.toString())) {
            byte[] res = map.get(key.toString());
            if (res.length == DataWord.BYTES) {
                return new DataWord(res);
            } else if (res.length == DoubleDataWord.BYTES) {
                return new DoubleDataWord(res);
            }
        }
        return storageErrorReturn;
    }

    @Override
    public List<byte[]> getPoolTx() {
        return null;
    }

    @Override
    public List<byte[]> getCacheTx() {
        return null;
    }

    @Override
    public BigInteger getBalance(Address addr) {
        return getAccountState(addr).getBalance();
    }

    @Override
    public BigInteger addBalance(Address addr, BigInteger value) {
        return getAccountState(addr).addToBalance(value);
    }

    @Override
    public IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> startTracking() {
        return new DummyRepo(this);
    }

    @Override
    public void flush() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void syncToRoot(byte[] root) {

    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isValidRoot(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBatch(Map<Address, AccountState> accountStates,
        Map<Address, IContractDetails<IDataWord>> contractDetailes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadAccountState(Address addr, Map<Address, AccountState> cacheAccounts,
        Map<Address, IContractDetails<IDataWord>> cacheDetails) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IRepository<AccountState, IDataWord, IBlockStoreBase<?, ?>> getSnapshotTo(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IBlockStoreBase<?, ?> getBlockStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addTxBatch(Map<byte[], byte[]> pendingTx, boolean isPool) {

    }

    @Override
    public void removeTxBatch(Set<byte[]> pendingTx, boolean isPool) {

    }

    @Override
    public void compact() {
        throw new UnsupportedOperationException(
            "The tracking cache cannot be compacted. \'Compact\' should be called on the tracked repository.");
    }

    @Override
    public boolean isIndexed(byte[] hash, long level) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSnapshot() {
        throw new UnsupportedOperationException();
    }

}
