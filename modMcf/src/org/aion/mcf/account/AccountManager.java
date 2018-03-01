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
package org.aion.mcf.account;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Account Manger Class
 */
public class AccountManager {

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.API.name());
    private static final int UNLOCK_MAX = 86400, // sec
            UNLOCK_DEFAULT = 60; // sec
    private static AccountManager inst = null;

    private Map<Address, Account> accounts = null;

    private AccountManager() {
        LOGGER.debug("<account-manager init>");
        accounts = new HashMap<>();
    }

    public static AccountManager inst() {
        if (inst == null)
            inst = new AccountManager();
        return inst;
    }

    // Retrieve ECKey from active accounts list from manager perspective
    // !important method. use in careful
    // Can use this method as check if unlocked
    public ECKey getKey(final Address _address) {

        Account acc = inst.accounts.get(_address);

        if (Optional.ofNullable(acc).isPresent()) {
            if (acc.getTimeout() >= Instant.now().getEpochSecond()) {
                return acc.getKey();
            } else {
                inst.accounts.remove(_address);
            }
        }

        return null;
    }

    public List<Account> getAccounts() {
        return inst.accounts.values().stream().collect(Collectors.toList());
    }

    public boolean unlockAccount(Address _address, String _password, int _timeout) {

        int timeout = UNLOCK_DEFAULT;
        if (_timeout > UNLOCK_MAX) {
            timeout = UNLOCK_MAX;
        } else if (_timeout > 0) {
            timeout = _timeout;
        }

        ECKey key = Keystore.getKey(_address.toString(), _password);

        if (Optional.ofNullable(key).isPresent()) {
            Account acc = inst.accounts.get(_address);

            long t = Instant.now().getEpochSecond() + timeout;
            if (Optional.ofNullable(acc).isPresent()) {
                acc.updateTimeout(t);
            } else {
                Account a = new Account(key, t);
                inst.accounts.put(_address, a);
            }

            LOGGER.debug("<unlock-success addr={}>", _address);
            return true;
        } else {
            LOGGER.debug("<unlock-fail addr={}>", _address);
            return false;
        }
    }

    public boolean lockAccount(Address _address, String _password) {

        ECKey key = Keystore.getKey(_address.toString(), _password);

        if (Optional.ofNullable(key).isPresent()) {
            Account acc = inst.accounts.get(_address);

            if (Optional.ofNullable(acc).isPresent()) {
                acc.updateTimeout(Instant.now().getEpochSecond() - 1);
            }

            LOGGER.debug("<lock-success addr={}>", _address);
            return true;
        } else {
            LOGGER.debug("<lock-fail addr={}>", _address);
            return false;
        }
    }
}
