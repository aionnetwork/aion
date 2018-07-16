package org.aion.gui.model.dto;

import org.aion.api.type.ApiMsg;
import org.aion.base.type.Address;
import org.aion.gui.model.KernelConnection;

import java.math.BigInteger;

/**
 * Adapted from ApiBlockchainConnector#getBalance(String) from https://github.com/aionnetwork/aion_ui
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
        super(kernelConnection);
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
            throwAndLogIfError(msg);
            balance = msg.getObject();
        } catch (Exception e) {
            balance = null;
        }
    }
}
