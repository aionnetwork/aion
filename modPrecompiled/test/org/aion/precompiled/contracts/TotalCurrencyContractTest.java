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

package org.aion.precompiled.contracts;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TotalCurrencyContractTest {
    private static final Address ADDR = ContractFactory.getTotalCurrencyContractAddress();
    private static final long COST = 21000L;
    private static final BigInteger AMT = BigInteger.valueOf(1000);
    private TotalCurrencyContract tcc;
    private IRepositoryCache repo;
    private ECKey ownerKey;

    @Before
    public void setup() {
        repo = new DummyRepo();
        ownerKey = ECKeyFac.inst().create();
        tcc = new TotalCurrencyContract(repo, ADDR, Address.wrap(ownerKey.getAddress()));
    }

    @After
    public void tearDown() {
        repo = null;
        ownerKey = null;
        tcc = null;
    }

    /**
     * Constructs the input for an update request on the TotalCurrencyContract using AMT as the
     * value to update by and signs it using ownerKey.
     *
     * @param chainID The chain to update.
     * @param signum 0 for addition, 1 for subtraction.
     * @return the input byte array.
     */
    private byte[] constructUpdateInput(byte chainID, byte signum) {
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.put(chainID).put(signum).put(new DataWord(AMT.toByteArray()).getData());

        byte[] payload = buffer.array();
        buffer = ByteBuffer.allocate(18 + 96);

        return buffer.put(payload).put(ownerKey.sign(payload).toBytes()).array();
    }

    // <------------------------------------------------------------------------------------------->

    @Test
    public void TestGetTotalAmount() {
        System.out.println("Running TestGetTotalAmount.");

        byte[] payload = new byte[] {0}; // input == chainID
        TransactionResult res = tcc.execute(payload, COST);

        System.out.println("Contract result: " + res.toString());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestGetTotalAmountEmptyPayload() {
        System.out.println("Running TestGetTotalAmountEmptyPayload.");

        byte[] payload = new byte[0]; // zero size input
        TransactionResult res = tcc.execute(payload, COST);

        System.out.println("Contract result: " + res.toString());
        assertEquals(ResultCode.FAILURE, res.getResultCode());
    }

    @Test
    public void TestGetTotalAmountInsufficientNrg() {
        System.out.println("Running TestGetTotalAmountInsufficientNrg");

        byte[] payload = new byte[] {0};
        TransactionResult res = tcc.execute(payload, COST - 1);

        assertEquals(ResultCode.OUT_OF_ENERGY, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void TestUpdateTotalAmount() {
        System.out.println("Running TestUpdateTotalAmount.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        TransactionResult res = tcc.execute(input, COST);

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestUpdateAndGetTotalAmount() {
        System.out.println("Running TestUpdateAndGetTotalAmount.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        TransactionResult res = tcc.execute(input, COST);

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());

        tcc = new TotalCurrencyContract(repo, ADDR, Address.wrap(ownerKey.getAddress()));
        input = new byte[] {(byte) 0x0};
        res = tcc.execute(input, COST);

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(AMT, new BigInteger(res.getOutput()));
    }

    @Test
    public void TestUpdateAndGetDiffChainIds() {
        System.out.println("Running TestUpdateAndGetDiffChainIds.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        TransactionResult res = tcc.execute(input, COST);

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());

        res = tcc.execute(new byte[] {(byte) 0x1}, COST); // query a diff chainID

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void TestMultipleUpdates() {
        System.out.println("Running TestMultipleUpdates.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        tcc.execute(input, COST);
        tcc.execute(input, COST);
        tcc.execute(input, COST);
        tcc.execute(input, COST);

        TransactionResult res = tcc.execute(new byte[] {(byte) 0x0}, COST);

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(AMT.multiply(BigInteger.valueOf(4)), new BigInteger(res.getOutput()));
    }

    @Test
    public void TestGetTotalInsufficientNrg() {
        System.out.println("Running TestUpdateTotalInsufficientNrg.");

        TransactionResult res = tcc.execute(new byte[] {(byte) 0x0}, COST - 1);
        assertEquals(ResultCode.OUT_OF_ENERGY, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestUpdateTotalIncorrectSigSize() {
        System.out.println("Running TestUpdateTotalIncorrectSigSize.");

        byte[] input =
                Arrays.copyOfRange(
                        constructUpdateInput((byte) 0x0, (byte) 0x0), 0, 100); // cut sig short.
        TransactionResult res = tcc.execute(input, COST);

        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestUpdateTotalNotOwner() {
        System.out.println("Running TestUpdateTotalNotOwner.");
        TotalCurrencyContract contract =
                new TotalCurrencyContract(
                        repo,
                        ADDR,
                        Address.wrap(ECKeyFac.inst().create().getAddress())); // diff owner.

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        TransactionResult res = contract.execute(input, COST);

        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestUpdateTotalIncorrectSig() {
        System.out.println("Running TestUpdateTotalIncorrectSig.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        input[30] = (byte) ~input[30]; // flip a bit

        TransactionResult res = tcc.execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestSubtractTotalAmount() {
        System.out.println("Running TestSubtractTotalAmount.");

        // First give some positive balance to take away.
        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x0);
        tcc.execute(input, COST);
        tcc.execute(input, COST);
        tcc.execute(input, COST);

        // Remove the balance.
        input = constructUpdateInput((byte) 0x0, (byte) 0x1);
        tcc.execute(input, COST);

        TransactionResult res = tcc.execute(new byte[] {(byte) 0x0}, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(AMT.multiply(BigInteger.valueOf(2)), new BigInteger(res.getOutput()));

        tcc.execute(input, COST);
        tcc.execute(input, COST);

        res = tcc.execute(new byte[] {(byte) 0x0}, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void TestSubtractTotalAmountBelowZero() {
        System.out.println("Running TestSubtractTotalAmountBelowZero.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x1); // 0x1 == subtract
        TransactionResult res = tcc.execute(input, COST);

        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());

        // Verify total amount is non-negative.
        res = tcc.execute(new byte[] {(byte) 0x0}, COST);
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void TestBadSignum() {
        System.out.println("Running TestBadSigum.");

        byte[] input = constructUpdateInput((byte) 0x0, (byte) 0x2); // only 0, 1 are valid.
        TransactionResult res = tcc.execute(input, COST);

        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0L, res.getEnergyRemaining());
    }

    @Test
    public void TestUpdateMultipleChains() {
        System.out.println("Running TestUpdateMultipleChains.");

        byte[] input0 = constructUpdateInput((byte) 0x0, (byte) 0x0);
        byte[] input1 = constructUpdateInput((byte) 0x1, (byte) 0x0);
        byte[] input2 = constructUpdateInput((byte) 0x10, (byte) 0x0);

        tcc.execute(input0, COST);
        tcc.execute(input1, COST);
        tcc.execute(input1, COST);
        tcc.execute(input2, COST);
        tcc.execute(input2, COST);
        tcc.execute(input2, COST);
        tcc.execute(input2, COST);

        TransactionResult res = tcc.execute(new byte[] {(byte) 0x0}, COST); // get chain 0.
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(AMT, new BigInteger(res.getOutput()));

        res = tcc.execute(new byte[] {(byte) 0x1}, COST); // get chain 1.
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(AMT.multiply(BigInteger.valueOf(2)), new BigInteger(res.getOutput()));

        res = tcc.execute((new byte[] {(byte) 0x10}), COST); // get chain 16.
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(AMT.multiply(BigInteger.valueOf(4)), new BigInteger(res.getOutput()));
    }
}
