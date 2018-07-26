package org.aion.gui.model.dto;

import org.aion.api.type.ApiMsg;
import org.aion.base.type.Address;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.SimpleApiMsgErrorHandler;

import java.math.BigInteger;

/**
 * Adapted from ApiBlockchainConnector#getBalance(String) from https://github.com/aionnetwork/aion_ui
 *
 * Note: to get the balance for an account, need to setAddress(String) then loadFromApi then getBalance().  Since
 * these things don't happen atomically, don't share the same instance of this class between two UI elements
 * otherwise they could interfere with one another.  Should probably refactor this to just have a less
 * complicated interface.
 */
public class BalanceDto extends AbstractDto {
    private String address;
    private BigInteger balance;

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public BalanceDto(KernelConnection kernelConnection) {
        super(kernelConnection, SimpleApiMsgErrorHandler.INSTANCE);
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigInteger getBalance() {
        return balance;
    }

    @Override
    protected void loadFromApiInternal() {
        try {
            ApiMsg msg = callApi(api -> api.getChain().getBalance(new Address(address)));
            balance = msg.getObject();
        } catch (Exception e) {
            balance = null;
        }
    }
}
