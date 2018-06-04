package org.aion.precompiled;

import static org.junit.Assert.assertFalse;

import java.math.BigInteger;
import java.util.List;
import org.aion.api.IAionAPI;
import org.aion.api.IUtils;
import org.aion.api.type.ApiMsg;
import org.aion.api.type.TxArgs;
import org.aion.api.type.TxArgs.TxArgsBuilder;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test cases related to calling the TotalCurrencyContract via a sendTransaction call through the
 * api.
 *
 * For tests to work set up accounts whose passwords are all "test".
 */
public class CallTotalCurrContractTest {
    private final String url = IAionAPI.LOCALHOST_URL;
    private final String pw = "test";
    private IAionAPI api;

    @Before
    public void setup() {
        this.api = IAionAPI.init();
        assertFalse(api.connect(url).isError());
    }

    @After
    public void tearDown() {
        this.api.destroyApi();
    }

    //<-------------------------------------------------------------------------------------------->

    @Test
    public void TestGetTotalCurrency() {
        ApiMsg res = api.getWallet().getAccounts();
        assertFalse(res.isError());
        List<Address> accounts = res.getObject();
        Address accountAddress = accounts.get(0);

        res = api.getWallet().unlockAccount(accountAddress, pw, 300);
        assertFalse(res.isError());

        TxArgs.TxArgsBuilder builder = new TxArgsBuilder()
            .data(ByteArrayWrapper.wrap(IUtils.hex2Bytes("00")))
            .from(accountAddress)
            .to(ContractFactory.getTotalCurrencyContractAddress())
            .nrgLimit(100000)
            .nrgPrice(10000000000L)
            .value(BigInteger.ONE)
            .nonce(BigInteger.ZERO);

        api.getTx().fastTxbuild(builder.createTxArgs());
        res = api.getTx().sendTransaction(null);
        assertFalse(res.isError());
    }

}
