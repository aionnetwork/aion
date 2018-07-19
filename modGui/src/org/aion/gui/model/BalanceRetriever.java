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

    public BigInteger getBalance(String address) {
        ApiMsg msg = callApi(api -> api.getChain().getBalance(new Address(address)));
        return msg.getObject();
    }
}
