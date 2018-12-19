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

package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.h256;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.Hex;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author jay */
public class AionRepositoryDummy extends AionRepositoryImpl {

    private static final Logger logger = LoggerFactory.getLogger("repository");
    private Map<ByteArrayWrapper, AccountState> worldState = new HashMap<>();
    private Map<ByteArrayWrapper, IContractDetails> detailsDB = new HashMap<>();

    public AionRepositoryDummy(IRepositoryConfig cfg) {
        super(cfg);
    }

    public void reset() {

        worldState.clear();
        detailsDB.clear();
    }

    public void close() {
        worldState.clear();
        detailsDB.clear();
    }

    public boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    public void updateBatch(
            HashMap<ByteArrayWrapper, AccountState> stateCache,
            HashMap<ByteArrayWrapper, IContractDetails> detailsCache) {

        for (ByteArrayWrapper hash : stateCache.keySet()) {

            AccountState accountState = stateCache.get(hash);
            IContractDetails contractDetails = detailsCache.get(hash);

            if (accountState.isDeleted()) {
                worldState.remove(hash);
                detailsDB.remove(hash);

                logger.debug("delete: [{}]", Hex.toHexString(hash.getData()));

            } else {

                if (accountState.isDirty() || contractDetails.isDirty()) {
                    detailsDB.put(hash, contractDetails);
                    accountState.setStateRoot(contractDetails.getStorageHash());
                    accountState.setCodeHash(h256(contractDetails.getCode()));
                    worldState.put(hash, accountState);
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "update: [{}],nonce: [{}] balance: [{}] \n [{}]",
                                Hex.toHexString(hash.getData()),
                                accountState.getNonce(),
                                accountState.getBalance(),
                                Hex.toHexString(contractDetails.getStorageHash()));
                    }
                }
            }
        }

        stateCache.clear();
        detailsCache.clear();
    }

    public void flush() {
        throw new UnsupportedOperationException();
    }

    public void rollback() {
        throw new UnsupportedOperationException();
    }

    public void commit() {
        throw new UnsupportedOperationException();
    }

    public void syncToRoot(byte[] root) {
        throw new UnsupportedOperationException();
    }

    public IRepositoryCache<?, ?> startTracking() {
        return new AionRepositoryCache(this);
    }

    public void dumpState(IAionBlock block, long nrgUsed, int txNumber, byte[] txHash) {}

    public Set<AionAddress> getAccountsKeys() {
        return null;
    }

    public Set<ByteArrayWrapper> getFullAddressSet() {
        return worldState.keySet();
    }

    public BigInteger addBalance(AionAddress addr, BigInteger value) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        BigInteger result = account.addToBalance(value);
        worldState.put(addr.toByteArrayWrapper(), account);

        return result;
    }

    public BigInteger getBalance(AionAddress addr) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            return BigInteger.ZERO;
        }

        return account.getBalance();
    }

    public ByteArrayWrapper getStorageValue(AionAddress addr, ByteArrayWrapper key) {
        IContractDetails details = getContractDetails(addr);
        ByteArrayWrapper value = (details == null) ? null : details.get(key);

        if (value != null && value.isZero()) {
            // TODO: remove when integrating the AVM
            // used to ensure FVM correctness
            throw new IllegalStateException("Zero values should not be returned by contract.");
        }

        return value;
    }

    public void addStorageRow(AionAddress addr, ByteArrayWrapper key, ByteArrayWrapper value) {
        IContractDetails details = getContractDetails(addr);

        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }
        details.put(key, value);
        detailsDB.put(addr.toByteArrayWrapper(), details);
    }

    public byte[] getCode(AionAddress addr) {
        IContractDetails details = getContractDetails(addr);

        if (details == null) {
            return null;
        }

        return details.getCode();
    }

    public void saveCode(AionAddress addr, byte[] code) {
        IContractDetails details = getContractDetails(addr);

        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }

        details.setCode(code);
        detailsDB.put(addr.toByteArrayWrapper(), details);
    }

    public BigInteger getNonce(AionAddress addr) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        return account.getNonce();
    }

    public BigInteger increaseNonce(AionAddress addr) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        account.incrementNonce();
        worldState.put(addr.toByteArrayWrapper(), account);

        return account.getNonce();
    }

    public BigInteger setNonce(AionAddress addr, BigInteger nonce) {

        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        account.setNonce(nonce);
        worldState.put(addr.toByteArrayWrapper(), account);

        return account.getNonce();
    }

    public void delete(AionAddress addr) {
        worldState.remove(addr.toByteArrayWrapper());
        detailsDB.remove(addr.toByteArrayWrapper());
    }

    public IContractDetails getContractDetails(AionAddress addr) {

        return detailsDB.get(addr.toByteArrayWrapper());
    }

    public AccountState getAccountState(AionAddress addr) {
        return worldState.get((addr.toByteArrayWrapper()));
    }

    public AccountState createAccount(AionAddress addr) {
        AccountState accountState = new AccountState();
        worldState.put(addr.toByteArrayWrapper(), accountState);

        IContractDetails contractDetails = this.cfg.contractDetailsImpl();
        detailsDB.put(addr.toByteArrayWrapper(), contractDetails);

        return accountState;
    }

    public boolean isExist(AionAddress addr) {
        return getAccountState(addr) != null;
    }

    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    public void loadAccount(
            AionAddress addr,
            HashMap<ByteArrayWrapper, AccountState> cacheAccounts,
            HashMap<ByteArrayWrapper, IContractDetails> cacheDetails) {

        AccountState account = getAccountState(addr);
        IContractDetails details = getContractDetails(addr);

        if (account == null) {
            account = new AccountState();
        } else {
            account = new AccountState(account);
        }

        if (details == null) {
            details = this.cfg.contractDetailsImpl();
        } else {
            details = new ContractDetailsCacheImpl(details);
        }

        cacheAccounts.put(addr.toByteArrayWrapper(), account);
        cacheDetails.put(addr.toByteArrayWrapper(), details);
    }
}
