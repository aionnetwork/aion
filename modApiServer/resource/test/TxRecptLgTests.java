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
 * Contributors:
 *     Aion foundation.
 */

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.aion.api.ITx.NRG_LIMIT_CONTRACT_CREATE_MAX;
import static org.aion.api.ITx.NRG_LIMIT_TX_MAX;
import static org.aion.api.ITx.NRG_PRICE_MIN;

import java.math.BigInteger;
import java.util.List;
import java.util.Scanner;
import org.aion.api.ITx;
import org.aion.api.type.ContractResponse;
import org.aion.api.type.TxLog;
import org.aion.api.type.TxReceipt;
import org.junit.Test;
import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.base.type.Address;
import org.aion.api.IContract;


public class TxRecptLgTests {

    private IAionAPI api = IAionAPI.init();
    private String pw = "";

    private static String readFile(String fileName) {
        StringBuilder contract = new StringBuilder();
        Scanner s = new Scanner(TxRecptLgTests.class.getResourceAsStream("contract/" + fileName));
        while (s.hasNextLine()) {
            contract.append(s.nextLine());
            contract.append("\n");
        }
        s.close();

        return contract.toString();
    }

    private boolean isEnoughBalance(Address address) {
        ApiMsg apiMsg = api.getChain().getBalance(address);
        assertFalse(apiMsg.isError());
        BigInteger balance = apiMsg.getObject();
        assertNotNull(balance);

        return balance.compareTo(BigInteger.valueOf(ITx.NRG_LIMIT_CONTRACT_CREATE_MAX)
                .multiply(BigInteger.valueOf(ITx.NRG_PRICE_MIN))) > 0;
    }

    @Test
    public void TestTxRecptLg() {
        System.out.println("run TestTxRecptLg.");

        ApiMsg apiMsg = api.connect(IAionAPI.LOCALHOST_URL);
        if (apiMsg.isError()) {
            System.out.format("Could not connect due to <%s>%n", apiMsg.getErrString());
            System.exit(-1);
        }

        apiMsg = api.getWallet().getAccounts();
        assertFalse(apiMsg.isError());

        List accs = apiMsg.getObject();
        assertNotNull(accs);
        if (accs.isEmpty()) {
            System.out.println("Empty account, skip this test!");
            return;
        }

        Address acc = (Address) accs.get(0);
        if (!isEnoughBalance(acc)) {
            System.out.println("Balance of the account is not enough, skip this test!");
            return;
        }

        apiMsg = api.getWallet().unlockAccount(acc, pw, 300);
        assertFalse(apiMsg.isError());
        assertTrue(apiMsg.getObject());

        // Deploy contract B
        String ctB = readFile("contractB.sol");
        api.getContractController().clear();
        apiMsg = api.getContractController()
                .createFromSource(ctB, acc, NRG_LIMIT_CONTRACT_CREATE_MAX, NRG_PRICE_MIN);
        assertFalse(apiMsg.isError());

        IContract contractB = api.getContractController().getContract();
        assertNotNull(contractB);
        assertEquals(contractB.getFrom(), acc);
        assertNotNull(contractB.getContractAddress());
        assertEquals(2, contractB.getAbiDefinition().size());

        Address ctAddrB = contractB.getContractAddress();

        // Deploy contract A
        String ctA = readFile("contractA.sol");
        ctA = ctA.replaceAll("0000000000000000000000000000000000000000000000000000000000001234", ctAddrB.toString());
        api.getContractController().clear();  // clear old deploy
        apiMsg = api.getContractController()
                .createFromSource(ctA, acc, NRG_LIMIT_CONTRACT_CREATE_MAX, NRG_PRICE_MIN);
        assertFalse(apiMsg.isError());

        IContract contractA = api.getContractController().getContract();
        assertNotNull(contractA);
        assertEquals(contractA.getFrom(), acc);
        assertNotNull(contractA.getContractAddress());
        assertEquals(4, contractA.getAbiDefinition().size());

        Address ctAddrA = contractA.getContractAddress();

        // Call function AA from deployed contractA
        apiMsg = contractA.newFunction("AA")
                .setFrom(acc)
                .setTxNrgLimit(NRG_LIMIT_TX_MAX)
                .setTxNrgPrice(NRG_PRICE_MIN)
                .build()
                .execute();
        assertFalse(apiMsg.isError());

        ContractResponse rsp = apiMsg.getObject();
        TxReceipt txReceipt = api.getTx().getTxReceipt(rsp.getTxHash()).getObject();
        List<TxLog> txRecptLgs = txReceipt.getTxLogs();

        assertEquals(ctAddrA, txRecptLgs.get(0).getAddress()); // AE
        assertEquals(ctAddrA, txRecptLgs.get(1).getAddress()); // AEA
        assertEquals(ctAddrB, txRecptLgs.get(2).getAddress()); // b.BB
        assertEquals(ctAddrA, txRecptLgs.get(3).getAddress()); // AEB

        api.destroyApi();
    }
}
