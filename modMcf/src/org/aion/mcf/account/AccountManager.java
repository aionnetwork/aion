package org.aion.mcf.account;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.types.AionAddress;
import org.slf4j.Logger;

/** Account Manger Class */
public class AccountManager {

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.API.name());
    public static final int UNLOCK_MAX = 86400, // sec
            UNLOCK_DEFAULT = 60; // sec

    private Map<AionAddress, Account> accounts;

    private AccountManager() {
        LOGGER.debug("<account-manager init>");
        accounts = new HashMap<>();
    }

    private static class Holder {
        static final AccountManager INSTANCE = new AccountManager();
    }

    public static AccountManager inst() {
        return Holder.INSTANCE;
    }

    // Retrieve ECKey from active accounts list from manager perspective
    // !important method. use in careful
    // Can use this method as check if unlocked
    public ECKey getKey(final AionAddress _address) {

        Account acc = this.accounts.get(_address);

        if (Optional.ofNullable(acc).isPresent()) {
            if (acc.getTimeout() >= Instant.now().getEpochSecond()) {
                return acc.getKey();
            } else {
                this.accounts.remove(_address);
            }
        }

        return null;
    }

    public List<Account> getAccounts() {
        return new ArrayList<>(this.accounts.values());
    }

    public boolean unlockAccount(AionAddress _address, String _password, int _timeout) {

        ECKey key = Keystore.getKey(_address.toString(), _password);

        if (Optional.ofNullable(key).isPresent()) {
            Account acc = this.accounts.get(_address);

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

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("<unlock-success addr={}>", _address);
            }
            return true;
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("<unlock-fail addr={}>", _address);
            }
            return false;
        }
    }

    public boolean lockAccount(AionAddress _address, String _password) {

        ECKey key = Keystore.getKey(_address.toString(), _password);

        if (Optional.ofNullable(key).isPresent()) {
            Account acc = this.accounts.get(_address);

            if (Optional.ofNullable(acc).isPresent()) {
                acc.updateTimeout(Instant.now().getEpochSecond() - 1);
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("<lock-success addr={}>", _address);
            }
            return true;
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("<lock-fail addr={}>", _address);
            }
            return false;
        }
    }
}
