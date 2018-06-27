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
import org.aion.precompiled.contracts.AionNameServiceContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

import static junit.framework.TestCase.assertEquals;

/**
 * Test of the Aion Auction Contract
 */

public class AionAuctionContractTest {
    private Address domainAddress1 = Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 = Address.wrap("a022222222222222222222222222222202020202020202020202020202020202");
    private String domainName1 = "bion.aion";
    private String domainName2 = "cion.aion.aion";
    private IRepositoryCache repo;
    private AionAuctionContract testAAC;

    private ECKey defaultKey;
    private BigInteger defaultBidAmount = new BigInteger("1000");
    private long DEFAULT_INPUT_NRG = 24000;
    private ECKey k;
    private ECKey k2;
    private ECKey k3;
    private ECKey k4;

    @Before
    public void setup() {
        repo = new DummyRepo();
        defaultKey = ECKeyFac.inst().create();
        testAAC = new AionAuctionContract(repo, domainAddress1);
        repo.createAccount(Address.wrap(defaultKey.getAddress()));
        repo.addBalance(Address.wrap(defaultKey.getAddress()), new BigInteger("4000000"));

        k = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();
        k4 = ECKeyFac.inst().create();
        repo.createAccount(Address.wrap(k.getAddress()));
        repo.createAccount(Address.wrap(k2.getAddress()));
        repo.createAccount(Address.wrap(k3.getAddress()));
        repo.createAccount(Address.wrap(k4.getAddress()));
        repo.addBalance(Address.wrap(k.getAddress()), new BigInteger("10000"));
        repo.addBalance(Address.wrap(k2.getAddress()), new BigInteger("10000"));
        repo.addBalance(Address.wrap(k3.getAddress()), new BigInteger("10000"));
        repo.addBalance(Address.wrap(k4.getAddress()), new BigInteger("10000"));
    }

    @After
    public void tearDown(){

    }

    // test along with ans
    @Test(expected = IllegalArgumentException.class)
    public void unregisteredDomain(){
        ECKey k = ECKeyFac.inst().create();
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
    }

    @Test()
    public void newTest(){
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined = setupInputs("12312421412.41dsfsdgsdg.aion", Address.wrap(defaultKey.getAddress()), amount.toByteArray(), defaultKey);
        AionAuctionContract aac = new AionAuctionContract(repo, domainAddress1);
        ContractExecutionResult result = aac.execute(combined, inputEnergy);

        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(4 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(ResultCode.SUCCESS, result.getCode());
    }

    //-------------------------------Auction Correctness Test------------------------------------//
    @Test()
    public void testWithANS() throws UnsupportedEncodingException {
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined = setupInputs(domainName2, Address.wrap(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, Address.wrap(k.getAddress()));
        ContractExecutionResult result = aac.execute(combined, inputEnergy);

        BigInteger amount4 = new BigInteger("6000");
        byte[] combined4 = setupInputs(domainName2, Address.wrap(k4.getAddress()), amount4.toByteArray(), k4);
        AionAuctionContract aac4 = new AionAuctionContract( repo, Address.wrap(k4.getAddress()));
        aac4.execute(combined4, inputEnergy);

        BigInteger amount2 = new BigInteger("5000");
        byte[] combined2 = setupInputs(domainName2, Address.wrap(k2.getAddress()), amount2.toByteArray(), k2);
        AionAuctionContract aac2 = new AionAuctionContract(repo, Address.wrap(k2.getAddress()));
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 = setupInputs(domainName2, Address.wrap(k3.getAddress()), amount3.toByteArray(), k3);
        AionAuctionContract aac3 = new AionAuctionContract(repo, Address.wrap(k3.getAddress()));
        aac3.execute(combined3, inputEnergy);

        // check balances after bidding
        assertEquals(9000, repo.getBalance(Address.wrap(k.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(Address.wrap(k2.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(Address.wrap(k3.getAddress())).intValue());
        assertEquals(4000, repo.getBalance(Address.wrap(k4.getAddress())).intValue()); // winner

        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after auction, balances should be returned
        assertEquals(10000, repo.getBalance(Address.wrap(k.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(Address.wrap(k2.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(Address.wrap(k3.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(Address.wrap(k4.getAddress())).intValue());

        aac.query();

        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, Address.wrap(result.getOutput()), Address.wrap(k4.getAddress()));
        System.out.println();

        try {
            Thread.sleep(4 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMain() throws UnsupportedEncodingException {
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined = setupInputs(domainName1, Address.wrap(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, domainAddress1);
        aac.execute(combined, inputEnergy);

        BigInteger amount2 = new BigInteger("3000");
        byte[] combined2 = setupInputs(domainName1, Address.wrap(k2.getAddress()), amount2.toByteArray(), k2);
        AionAuctionContract aac2 = new AionAuctionContract(repo, domainAddress1);
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 = setupInputs(domainName1, Address.wrap(k3.getAddress()), amount3.toByteArray(), k3);
        AionAuctionContract aac3 = new AionAuctionContract(repo, domainAddress1);
        aac3.execute(combined3, inputEnergy);

        BigInteger amount4 = new BigInteger("5000");
        byte[] combined4 = setupInputs(domainName1, Address.wrap(k4.getAddress()), amount4.toByteArray(), k4);
        AionAuctionContract aac4 = new AionAuctionContract( repo, domainAddress1);
        aac4.execute(combined4, inputEnergy);

        try {
            Thread.sleep(1 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after bidding
        assertEquals(9000, repo.getBalance(Address.wrap(k.getAddress())).intValue());
        assertEquals(7000, repo.getBalance(Address.wrap(k2.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(Address.wrap(k3.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(Address.wrap(k4.getAddress())).intValue());

        BigInteger amount6 = new BigInteger("2000");
        byte[] combined6 = setupInputs(domainName2, Address.wrap(k.getAddress()), amount6.toByteArray(), k);
        AionAuctionContract aac6 = new AionAuctionContract(repo, domainAddress2);
        aac6.execute(combined6, inputEnergy);

        BigInteger amount7 = new BigInteger("4000");
        byte[] combined7 = setupInputs(domainName2, Address.wrap(k2.getAddress()), amount7.toByteArray(), k2);
        AionAuctionContract aac7 = new AionAuctionContract(repo, domainAddress2);
        aac7.execute(combined7, inputEnergy);

        // check balances after bidding both domains
        assertEquals(7000, repo.getBalance(Address.wrap(k.getAddress())).intValue());
        assertEquals(3000, repo.getBalance(Address.wrap(k2.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(Address.wrap(k3.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(Address.wrap(k4.getAddress())).intValue());

        aac.query();

        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after both auctions are complete, winners should have their deposits gone
        assertEquals(10000, repo.getBalance(Address.wrap(k.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(Address.wrap(k2.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(Address.wrap(k3.getAddress())).intValue());
        assertEquals(7000, repo.getBalance(Address.wrap(k4.getAddress())).intValue());

        aac.query();

        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after both domains become inactive, all accounts should have their original balance
        assertEquals(10000, repo.getBalance(Address.wrap(k.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(Address.wrap(k2.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(Address.wrap(k3.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(Address.wrap(k4.getAddress())).intValue());

        aac.query();
    }

    //-------------------------------------Basic Tests ------------------------------------------//
    @Test()
    public void testInvalidDomainNames(){
        byte[] combined = setupInputs("aa.aion", Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        AionAuctionContract aac = new AionAuctionContract(repo, domainAddress1);
        ContractExecutionResult result = aac.execute(combined, DEFAULT_INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());

        byte[] combined2 = setupInputs("#$%aion.aion", Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        AionAuctionContract aac2 = new AionAuctionContract(repo, domainAddress1);
        ContractExecutionResult result2 = aac2.execute(combined2, DEFAULT_INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, result2.getCode());

        byte[] combined3 = setupInputs("withoutdotaion", Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        AionAuctionContract aac3 = new AionAuctionContract(repo, domainAddress1);
        ContractExecutionResult result3 = aac3.execute(combined3, DEFAULT_INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, result3.getCode());

        byte[] combined4 = setupInputs("ai.ai.ai.ai.aion", Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        AionAuctionContract aac4 = new AionAuctionContract(repo, domainAddress1);
        ContractExecutionResult result4 = aac4.execute(combined4, DEFAULT_INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, result4.getCode());
    }

    @Test
    public void insufficientBalance(){
        ECKey poorKey = ECKeyFac.inst().create();
        repo.createAccount(Address.wrap(poorKey.getAddress()));
        repo.addBalance(Address.wrap(poorKey.getAddress()), new BigInteger("100"));

        byte[] combined3 = setupInputs(domainName1, Address.wrap(poorKey.getAddress()), defaultBidAmount.toByteArray(), poorKey);
        ContractExecutionResult result = testAAC.execute(combined3, DEFAULT_INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
    }

    @Test
    public void testIncorrectInputLength(){
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        byte[] wrongInput = new byte[130];
        System.arraycopy(input, 0, wrongInput, 0, 130);
        ContractExecutionResult result = testAAC.execute(wrongInput, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testIncorrectSignature(){
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
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
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), anotherKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testInsufficientEnergy(){
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, 18000);

        assertEquals(ResultCode.OUT_OF_NRG, result.getCode());
        assertEquals(result.getNrgLeft(), 0);
    }

    @Test
    public void testNegativeBidValue(){
        BigInteger negativeBidAmount = new BigInteger("-100");
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), negativeBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(ResultCode.INTERNAL_ERROR, result.getCode());
        assertEquals(result.getNrgLeft(), 4000);
    }

    @Test
    public void testRequestedDomainIsAlreadyActive(){
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        testAAC.execute(input, DEFAULT_INPUT_NRG);

        // wait more than 4 seconds for auction to finish and domainAddress1 to become active
        // times in the contract class need to be set for testing
        try {
            Thread.sleep(4 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // setup a new aac to call and request a domain that is already active
        BigInteger bidAmount2 = new BigInteger("2000");
        AionAuctionContract aac2 = new AionAuctionContract(repo, domainAddress1);
        byte[] input2 = setupInputs(domainName1, Address.wrap(k2.getAddress()), bidAmount2.toByteArray(), k2);
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
        BigInteger newBidAmount = new BigInteger("10");
        byte[] input2 = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), newBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        BigInteger anotherBid = new BigInteger("2000");
        byte[] input3 = setupInputs(domainName1, Address.wrap(k2.getAddress()), anotherBid.toByteArray(), k2);
        ContractExecutionResult result3 = testAAC.execute(input3, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(8 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testOverwritingBidsWithLargerValue(){
        BigInteger anotherBid = new BigInteger("2000");
        byte[] input3 = setupInputs(domainName1, Address.wrap(k2.getAddress()), anotherBid.toByteArray(), k2);
        ContractExecutionResult result3 = testAAC.execute(input3, DEFAULT_INPUT_NRG);

        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        BigInteger newBidAmount = new BigInteger("10000");
        byte[] input2 = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), newBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(8 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRequestInactiveDomain(){
        byte[] input = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        testAAC.execute(input, DEFAULT_INPUT_NRG);

        ECKey k3 = ECKeyFac.inst().create();
        AionAuctionContract aac3 = new AionAuctionContract(repo, Address.wrap(k3.getAddress()));
        BigInteger anotherAmount = new BigInteger("5000");
        byte[] input3 = setupInputs(domainName1, Address.wrap(k3.getAddress()), anotherAmount.toByteArray(), k3);
        aac3.execute(input3, DEFAULT_INPUT_NRG);

        // let the domain become inactive,
        // times in the contract class need to be set for testing
        try {
            Thread.sleep(8 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        byte[] input2 = setupInputs(domainName1, Address.wrap(defaultKey.getAddress()), defaultBidAmount.toByteArray(), defaultKey);
        ContractExecutionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(8 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(ResultCode.SUCCESS, result2.getCode());
        assertEquals(result2.getNrgLeft(), 4000);
    }

    private byte[] setupInputs(String domainName, Address ownerAddress, byte[] amount, ECKey k){
        int domainLength = domainName.length();
        int amountLength = amount.length;
        int offset = 0;
        byte[] ret = new byte[1 + domainLength + 32 + 96 + 1 + amountLength];

        ISignature signature = k.sign(ownerAddress.toBytes());

        System.arraycopy(new byte[]{(byte)domainLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(domainName.getBytes(), 0, ret, offset, domainLength);
        offset = offset + domainLength;
        System.arraycopy(ownerAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[]{(byte)amountLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amountLength);

        return ret;
    }

}