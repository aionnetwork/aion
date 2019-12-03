package org.aion.api.server.external.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.aion.crypto.ECKey;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.rpc.errors.RPCExceptions.InvalidParamsRPCException;
import org.aion.zero.impl.keystore.Keystore;
import org.aion.types.AionAddress;
import org.slf4j.Logger;

/** Account Manager Class */
public final class AccountManager implements AccountManagerInterface{

    private final Logger logger;
    static final int UNLOCK_MAX = 86400, // sec
            UNLOCK_DEFAULT = 60; // sec

    private static final int ACCOUNT_CACHE_SIZE = 1024;

    private Cache<AionAddress, Account> accounts;

    public AccountManager(Logger log) {
        logger = log;
        if (logger != null) {
            logger.debug("<account-manager init>");
        }

        accounts = Caffeine.newBuilder()
            .maximumSize(ACCOUNT_CACHE_SIZE)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();
    }

    @VisibleForTesting
    @Override
    public void removeAllAccounts() {
        accounts.cleanUp();
    }

    // Retrieve ECKey from active accounts list from manager perspective
    // !important method. use in careful
    // Can use this method as check if unlocked
    @Override
    public ECKey getKey(final AionAddress _address) {

        Account acc = this.accounts.getIfPresent(_address);

        if (Optional.ofNullable(acc).isPresent()) {
            if (acc.getTimeout() >= Instant.now().getEpochSecond()) {
                return acc.getKey();
            } else {
                this.accounts.invalidate(_address);
            }
        }

        return null;
    }

    @Override
    public List<Account> getAccounts() {
        return new ArrayList<>(this.accounts.asMap().values());
    }

    @Override
    public synchronized boolean unlockAccount(AionAddress _address, String _password, int _timeout) {

        ECKey key = Keystore.getKey(_address.toString(), _password);

        if (Optional.ofNullable(key).isPresent()) {
            Account acc = this.accounts.getIfPresent(_address);

            int timeout = UNLOCK_DEFAULT;
            if (_timeout > UNLOCK_MAX) {
                timeout = UNLOCK_MAX;
            } else if (_timeout > 0) {
                timeout = _timeout;
            }

            long t = Instant.now().getEpochSecond() + timeout;
            if (Optional.ofNullable(acc).isPresent()) {
                acc.updateTimeout(t);
            } else {
                Account a = new Account(key, t);
                this.accounts.put(_address, a);
            }

            if (logger != null) {
                logger.debug("<unlock-success addr={}>", _address);
            }
            return true;
        } else {
            if (logger != null) {
                logger.debug("<unlock-fail addr={}>", _address);
            }
            return false;
        }
    }

    @Override
    public AionAddress createAccount(String password) {
        String address = Keystore.create(password);
        ECKey key = Keystore.getKey(address, password);
        logger.debug("<create-success addr={}>",address);
        AionAddress aionAddress = new AionAddress(key.getAddress());
        this.accounts.put(aionAddress, new Account(key, 0));
        return aionAddress;
    }

    public synchronized boolean lockAccount(AionAddress _address, String _password) {

        ECKey key = Keystore.getKey(_address.toString(), _password);

        if (Optional.ofNullable(key).isPresent()) {
            Account acc = this.accounts.getIfPresent(_address);

            if (Optional.ofNullable(acc).isPresent()) {
                acc.updateTimeout(Instant.now().getEpochSecond() - 1);
            }

            if (logger != null) {
                logger.debug("<lock-success addr={}>", _address);
            }
            return true;
        } else {
            if (logger != null) {
                logger.debug("<lock-fail addr={}>", _address);
            }
            return false;
        }
    }
}
