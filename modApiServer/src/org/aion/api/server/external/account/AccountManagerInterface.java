package org.aion.api.server.external.account;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.types.AionAddress;

public interface AccountManagerInterface {

    /**
     * Cleans up the manager
     */
    @VisibleForTesting
    void removeAllAccounts();

    /**
     *
     * @param address the aion address of the key
     * @return the eckey
     */
    ECKey getKey(final AionAddress address);

    /**
     *
     * @return all the accounts known by the account manager
     */
    List<Account> getAccounts();

    /**
     *
     * @param address the account address
     * @param password the account password
     * @return true if the account could be locked
     */
    boolean lockAccount(AionAddress address, String password);

    /**
     *
     * @param address the account address
     * @param password the account password
     * @param timeout the length of time in seconds that this account should remain unlocked
     * @return true if the account could be unlocked
     */
    boolean unlockAccount(AionAddress address, String password, int timeout);

    /**
     * Creates a new account
     * @param password the password of the new account
     * @return the account address
     */
    AionAddress createAccount(String password);
}
