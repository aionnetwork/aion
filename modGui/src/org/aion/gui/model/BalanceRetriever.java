package org.aion.gui.model;

import org.aion.api.type.ApiMsg;
import org.aion.base.type.Address;

import java.math.BigInteger;

public class BalanceRetriever extends AbstractAionApiClient {

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public BalanceRetriever(KernelConnection kernelConnection) {
        super(kernelConnection);
    }

    /**
     * Get balance of given account
     *
     * @param address address of account
     * @return if API is connected, balance of that account; otherwise, null
     */
    public BigInteger getBalance(String address) {
        // TODO Prefer Optional over null.
        final BigInteger balance;
        if(!apiIsConnected()) {
            balance = null;
        } else {
            ApiMsg msg = callApi(api -> api.getChain().getBalance(new Address(address)));
            balance = msg.getObject();
        }
        return balance;
    }
}
