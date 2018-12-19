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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
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
import org.aion.vm.api.interfaces.Address;
import org.slf4j.Logger;

/** Account Manger Class */
public class AccountManager {

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.API.name());
    public static final int UNLOCK_MAX = 86400, // sec
            UNLOCK_DEFAULT = 60; // sec

    private Map<Address, Account> accounts;

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
    public ECKey getKey(final Address _address) {

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

    public boolean unlockAccount(Address _address, String _password, int _timeout) {

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

    public boolean lockAccount(Address _address, String _password) {

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
