package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.h256;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.conversions.Hex;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author jay */
public class AionRepositoryDummy extends AionRepositoryImpl {

    private static final Logger logger = LoggerFactory.getLogger("repository");
    private Map<ByteArrayWrapper, AccountState> worldState = new HashMap<>();
    private Map<ByteArrayWrapper, ContractDetails> detailsDB = new HashMap<>();

    public AionRepositoryDummy(RepositoryConfigImpl cfg) {
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
            HashMap<ByteArrayWrapper, ContractDetails> detailsCache) {

        for (ByteArrayWrapper hash : stateCache.keySet()) {

            AccountState accountState = stateCache.get(hash);
            ContractDetails contractDetails = detailsCache.get(hash);

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

    public RepositoryCache<?, ?> startTracking() {
        return new AionRepositoryCache(this);
    }

    public void dumpState(IAionBlock block, long nrgUsed, int txNumber, byte[] txHash) {}

    public Set<Address> getAccountsKeys() {
        return null;
    }

    public Set<ByteArrayWrapper> getFullAddressSet() {
        return worldState.keySet();
    }

    public BigInteger addBalance(Address addr, BigInteger value) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        BigInteger result = account.addToBalance(value);
        worldState.put(ByteArrayWrapper.wrap(addr.toBytes()), account);

        return result;
    }

    public BigInteger getBalance(Address addr) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            return BigInteger.ZERO;
        }

        return account.getBalance();
    }

    public ByteArrayWrapper getStorageValue(Address addr, ByteArrayWrapper key) {
        ContractDetails details = getContractDetails(addr);
        ByteArrayWrapper value = (details == null) ? null : details.get(key);

        if (value != null && value.isZero()) {
            // TODO: remove when integrating the AVM
            // used to ensure FVM correctness
            throw new IllegalStateException(
                    "The contract address "
                            + addr.toString()
                            + " returned a zero value for the key "
                            + key.toString()
                            + " which is not a valid stored value for the FVM. ");
        }

        return value;
    }

    // never used
    //    public void addStorageRow(Address addr, ByteArrayWrapper key, ByteArrayWrapper value) {
    //        ContractDetails details = getContractDetails(addr);
    //
    //        if (details == null) {
    //            createAccount(addr);
    //            details = getContractDetails(addr);
    //        }
    //        details.put(key, value);
    //        detailsDB.put(ByteArrayWrapper.wrap(addr.toBytes()), details);
    //    }

    public byte[] getCode(Address addr) {
        ContractDetails details = getContractDetails(addr);

        if (details == null) {
            return null;
        }

        return details.getCode();
    }

    public void saveCode(Address addr, byte[] code) {
        ContractDetails details = getContractDetails(addr);

        if (details == null) {
            createAccount(addr);
            details = getContractDetails(addr);
        }

        details.setCode(code);
        detailsDB.put(ByteArrayWrapper.wrap(addr.toBytes()), details);
    }

    public BigInteger getNonce(Address addr) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        return account.getNonce();
    }

    public BigInteger increaseNonce(Address addr) {
        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        account.incrementNonce();
        worldState.put(ByteArrayWrapper.wrap(addr.toBytes()), account);

        return account.getNonce();
    }

    public BigInteger setNonce(Address addr, BigInteger nonce) {

        AccountState account = getAccountState(addr);

        if (account == null) {
            account = createAccount(addr);
        }

        account.setNonce(nonce);
        worldState.put(ByteArrayWrapper.wrap(addr.toBytes()), account);

        return account.getNonce();
    }

    public void delete(Address addr) {
        worldState.remove(ByteArrayWrapper.wrap(addr.toBytes()));
        detailsDB.remove(ByteArrayWrapper.wrap(addr.toBytes()));
    }

    public ContractDetails getContractDetails(Address addr) {

        return detailsDB.get(ByteArrayWrapper.wrap(addr.toBytes()));
    }

    public AccountState getAccountState(Address addr) {
        return worldState.get((ByteArrayWrapper.wrap(addr.toBytes())));
    }

    public AccountState createAccount(Address addr) {
        AccountState accountState = new AccountState();
        worldState.put(ByteArrayWrapper.wrap(addr.toBytes()), accountState);

        ContractDetails contractDetails = this.cfg.contractDetailsImpl();
        detailsDB.put(ByteArrayWrapper.wrap(addr.toBytes()), contractDetails);

        return accountState;
    }

    public boolean isExist(Address addr) {
        return getAccountState(addr) != null;
    }

    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    public void loadAccount(
            Address addr,
            HashMap<ByteArrayWrapper, AccountState> cacheAccounts,
            HashMap<ByteArrayWrapper, ContractDetails> cacheDetails) {

        AccountState account = getAccountState(addr);
        ContractDetails details = getContractDetails(addr);

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

        cacheAccounts.put(ByteArrayWrapper.wrap(addr.toBytes()), account);
        cacheDetails.put(ByteArrayWrapper.wrap(addr.toBytes()), details);
    }
}
