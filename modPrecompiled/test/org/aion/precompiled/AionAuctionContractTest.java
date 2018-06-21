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
package org.aion.precompiled;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.precompiled.contracts.AionAuctionContract;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.junit.Before;
import org.junit.Test;
import java.math.BigInteger;

import static junit.framework.TestCase.assertEquals;

/**
 * Test of the Aion Auction Contract
 */

public class AionAuctionContractTest {
    private Address domainAddress1 = Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 = Address.wrap("a022222222222222222222222222222202020202020202020202020202020202");
    private IRepositoryCache repo;
    private AionAuctionContract testAAC;

    private ECKey defaultKey = ECKeyFac.inst().create();
    private BigInteger defaultBidAmount = new BigInteger("1000");
    private long DEFAULT_INPUT_NRG = 24000;

    @Before
    public void setup() {
        repo = new DummyRepo();
        testAAC = new AionAuctionContract(repo, domainAddress1);
    }

    //-------------------------------Auction Correctness Test------------------------------------//
    @Test
    public void testMain(){
        final long inputEnergy = 24000L;
        ECKey k = ECKeyFac.inst().create();
        ECKey k2 = ECKeyFac.inst().create();
        ECKey k3 = ECKeyFac.inst().create();
        ECKey k4 = ECKeyFac.inst().create();
        ECKey k5 = ECKeyFac.inst().create();

        BigInteger amount = new BigInteger("1000");
        byte[] combined = setupInputs(domainAddress1, Address.wrap(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, domainAddress1);
        aac.execute(combined, inputEnergy);

        BigInteger amount2 = new BigInteger("5000");
        byte[] combined2 = setupInputs(domainAddress1, Address.wrap(k2.getAddress()), amount2.toByteArray(), k2);
        AionAuctionContract aac2 = new AionAuctionContract(repo, domainAddress1);
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 = setupInputs(domainAddress1, Address.wrap(k3.getAddress()), amount3.toByteArray(), k3);
        AionAuctionContract aac3 = new AionAuctionContract(repo, domainAddress1);
        aac3.execute(combined3, inputEnergy);

        BigInteger amount4 = new BigInteger("5000");
        byte[] combined4 = setupInputs(domainAddress1, Address.wrap(k4.getAddress()), amount4.toByteArray(), k4);
        AionAuctionContract aac4 = new AionAuctionContract( repo, domainAddress1);
        aac4.execute(combined4, inputEnergy);

        BigInteger amount5 = new BigInteger("2000");
        byte[] combined5 = setupInputs(domainAddress1, Address.wrap(k5.getAddress()), amount5.toByteArray(), k5);
        AionAuctionContract aac5 = new AionAuctionContract( repo, domainAddress1);
        aac5.execute(combined5, inputEnergy);

        try {
            Thread.sleep(2 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BigInteger amount6 = new BigInteger("2000");
        byte[] combined6 = setupInputs(domainAddress2, Address.wrap(k.getAddress()), amount6.toByteArray(), k);
        AionAuctionContract aac6 = new AionAuctionContract(repo, domainAddress2);
        aac6.execute(combined6, inputEnergy);

        BigInteger amount7 = new BigInteger("4000");
        byte[] combined7 = setupInputs(domainAddress2, Address.wrap(k2.getAddress()), amount7.toByteArray(), k2);
        AionAuctionContract aac7 = new AionAuctionContract(repo, domainAddress2);
        aac7.execute(combined7, inputEnergy);

        try {
            Thread.sleep(10 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(8 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //-------------------------------------Basic Tests ------------------------------------------//
    @Test
    public void testInvalidInput(){
        // do some more test THINKING
    }

    @Test
    public void testIncorrectInputLength(){
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        byte[] wrongInput = new byte[160];
        System.arraycopy(input, 0, wrongInput, 0, 160);
        ContractExecutionResult result = testAAC.execute(wrongInput, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testIncorrectSignature(){
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        // modify the signature in the 65th byte (arbitrarily)
        input[110] = (byte) ~input[65];
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testIncorrectPublicKey(){
        ECKey anotherKey = ECKeyFac.inst().create();
        // use another key as the input
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), anotherKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testInsufficientEnergy(){
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, 18000);

        assertEquals(ResultCode.OUT_OF_NRG, result.getCode());
        assertEquals(result.getNrgLeft(), 0);
    }

    @Test
    public void testNegativeBidValue(){
        BigInteger negativeBidAmount = new BigInteger("-100");
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), negativeBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testRequestedDomainIsAlreadyActive(){
        //
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        // wait more than 6 seconds for auction to finish and domainAddress1 to become active
        // times in the contract class need to be set for testing
        try {
            Thread.sleep(6 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // setup a new aac to call and request a domain that is already active
        ECKey k2 = ECKeyFac.inst().create();
        BigInteger bidAmount2 = new BigInteger("2000");
        AionAuctionContract aac2 = new AionAuctionContract(repo, domainAddress1);

        byte[] input2 = setupInputs(domainAddress1, Address.wrap(k2.getAddress()), bidAmount2.toByteArray(), k2);
        ContractExecutionResult result2 = aac2.execute(input2, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.FAILURE, result2.getCode());
        assertEquals(result2.getNrgLeft(), 4000);
    }

    @Test
    public void testNoBidsError(){
        //currently not possible, since to start an auction, a first bid is needed
    }

    @Test
    public void testOverwritingBidsWithSmallerValue(){
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        BigInteger newBidAmount = new BigInteger("10");
        byte[] input2 = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), newBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(16 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testOverwritingBidsWithLargerValue(){
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        BigInteger newBidAmount = new BigInteger("10000");
        byte[] input2 = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), newBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(16 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRequestInactiveDomain(){
        byte[] input = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        testAAC.execute(input, DEFAULT_INPUT_NRG);

        ECKey k3 = ECKeyFac.inst().create();
        AionAuctionContract aac3 = new AionAuctionContract(repo, Address.wrap(k3.getAddress()));
        BigInteger anotherAmount = new BigInteger("5000");
        byte[] input3 = setupInputs(domainAddress1, Address.wrap(k3.getAddress()), anotherAmount.toByteArray(), k3);
        aac3.execute(input3, DEFAULT_INPUT_NRG);

        // let the domain become inactive,
        // times in the contract class need to be set for testing
        try {
            Thread.sleep(16 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        byte[] input2 = setupInputs(domainAddress1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.SUCCESS, result2.getCode());
        assertEquals(result2.getNrgLeft(), 4000);
    }

    private byte[] setupInputs(Address domainAddress, Address ownerAddress, byte[] amount, ECKey k){
        byte[] ret = new byte[32 + 32 + 96 + 1 + amount.length];
        int offset = 0;

        ISignature signature = k.sign(ownerAddress.toBytes());

        System.arraycopy(domainAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(ownerAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[]{(byte)amount.length}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amount.length);

        return ret;
    }
}
