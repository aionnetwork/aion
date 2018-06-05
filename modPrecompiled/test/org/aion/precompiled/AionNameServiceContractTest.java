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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.aion.crypto.HashUtil.blake128;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ISignature;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.AionNameServiceContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AionNameServiceContractTest {

    private static final String RESOLVER_HASH = "ResolverHash";
    private static final String OWNER_HASH = "OwnerHash";
    private static final String TTL_HASH = "TTLHash";

    private String domainName1 = "aion";
    private String domainName2 = "aion.aion"; // subdomain of domainName1
    private String domainName3 = "aion.bion"; // subdomain of domainName1
    private String domainName4 = "aion.bion.cion"; // subdomain of domainName1 and domainName3
    private String domainName5 = "aion.bion.dion"; // subdomain of domainName1 and domainName3
    private String notSubdomain = "bion.aion"; // not a subdomain of domainName1

    // private Address domainAddress = Address.wrap();
    private Address emptyAddress =
            Address.wrap("0000000000000000000000000000000000000000000000000000000000000000");
    private Address domainAddress1 =
            Address.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 =
            Address.wrap("a022222222222222222222222222222202020202020202020202020202020202");
    private Address domainAddress3 =
            Address.wrap("a033333333333333333333333333333303030303030303030303030303030303");
    private Address domainAddress4 =
            Address.wrap("a044444444444444444444444444444404040404040404040404040404040404");
    private Address domainAddress5 =
            Address.wrap("a055555555555555555555555555555505050505050050505050505050505050");
    private Address domainAddress6 =
            Address.wrap("a066666666666666666666666666666606060606060606060606060606060060");
    private Address invalidDomainAddress =
            Address.wrap("b066666666666666666666666666666606060606060606060606060606060606");

    private Address newAddress1 =
            Address.wrap("1000000000000000000000000000000000000000000000000000000000000001");
    private Address newAddress2 =
            Address.wrap("0100000000000000000000000000000000000000000000000000000000000010");
    private Address newAddress3 =
            Address.wrap("0010000000000000000000000000000000000000000000000000000000000100");
    private Address newAddress4 =
            Address.wrap("0001000000000000000000000000000000000000000000000000000000001000");
    private Address newAddress5 =
            Address.wrap("0000100000000000000000000000000000000000000000000000000000010000");
    private Address newAddress6 =
            Address.wrap("0000010000000000000000000000000000000000000000000000000000100000");
    private Address newAddress7 =
            Address.wrap("0000001000000000000000000000000000000000000000000000000001000000");
    private Address newAddress8 =
            Address.wrap("0000000100000000000000000000000000000000000000000000000010000000");

    @Before
    public void setup() {}

    @After
    public void tearDown() {
        AionNameServiceContract.clearDomainList();
    }

    @Test // try to create errors
    public void testInvalidValues() {
        final long inputEnergy = 5000L;
        ECKey k = ECKeyFac.inst().create();
        ECKey k2 = ECKeyFac.inst().create();
        ECKey k3 = ECKeyFac.inst().create();
        ECKey k4 = ECKeyFac.inst().create();
        ECKey k5 = ECKeyFac.inst().create();
        ECKey k6 = ECKeyFac.inst().create();

        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k, k2, k3, k4, k5, k6});
        repo.createAccount(newAddress2);
        repo = populateRepo(repo, new ECKey[] {k, k2, k3, k4, k5});
        repo.createAccount(newAddress1);

        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k2.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x4, k, newAddress3, "ai" +
                "on", "aion.aion");
        byte[] combined2 = setupInputs(newAddress2, (byte) 0x0, (byte) 0x4, k, newAddress3, "aion", "aion.aion");

        //trying to access domain with wrong address
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        ContractExecutionResult res2 = ansc2.execute(combined2, inputEnergy);

        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(3000L, res.getNrgLeft());

        assertEquals(ResultCode.INTERNAL_ERROR, res2.getCode());
        assertEquals(0, res2.getNrgLeft());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidDomainAddress() {
        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = populateRepo();
        createAccounts(repo, new ECKey[] {k});
        repo.addContract(newAddress1, new byte[100]);

        //  domain addresses must have aion prefix: 0xa0(66char) or a0(64char)
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo,
                        invalidDomainAddress,
                        Address.wrap(k.getAddress()));
        assertNull(ansc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidDomainOwnerAddress() {
        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = populateRepo();
        createAccounts(repo, new ECKey[] {k});

        // The owner address need to exist in the given repoistory, as an account or smart contract
        AionNameServiceContract ansc =
                new AionNameServiceContract((IRepositoryCache) repo, domainAddress2, newAddress1);
        assertNull(ansc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConflictOwnerAddress() {
        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = populateRepo();
        createAccounts(repo, new ECKey[] {k});

        // check that the given owner address is the same as the owner address in the repository.
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k.getAddress()));
        assertNull(ansc);
    }

    //    @Test
    //    public void testInvalidDomainName(){
    //        final long inputEnergy = 5000L;
    //        ECKey k = ECKeyFac.inst().create();
    //        ECKey k2 = ECKeyFac.inst().create();
    //        ECKey k3 = ECKeyFac.inst().create();
    //        ECKey k4 = ECKeyFac.inst().create();
    //        ECKey k5 = ECKeyFac.inst().create();
    //
    //        DummyRepo repo = populateRepo();
    //        createAccounts(repo, new ECKey[]{k, k2, k3, k4, k5});
    //        repo = populateRepo(repo, new ECKey[] {k, k2, k3, k4, k5});
    //
    //        AionNameServiceContract ansc =
    //                new AionNameServiceContract(
    //                        (IRepositoryCache) repo, domainAddress1,
    // Address.wrap(k.getAddress()));
    //
    //        byte[] combined = setupInputs(newAddress1, (byte)0x0, (byte)0x4, k, newAddress2,
    // domainName1, domainName2);
    //        byte[] combined2 = setupInputs(newAddress2, (byte)0x0, (byte)0x4, k,
    // Address.wrap(k2.getAddress()), domainName1, domainName3);
    //        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
    //        ContractExecutionResult res2 = ansc.execute(combined2, inputEnergy);
    //        assertEquals(ResultCode.INTERNAL_ERROR ,res2.getCode());
    //    }

    @Test
    public void bigTest2() {
        final long inputEnergy = 5000L;
        ECKey k = ECKeyFac.inst().create();
        ECKey k2 = ECKeyFac.inst().create();
        ECKey k3 = ECKeyFac.inst().create();
        ECKey k4 = ECKeyFac.inst().create();
        ECKey k5 = ECKeyFac.inst().create();

        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k, k2, k3, k4, k5});
        // populate the storage database
        repo = populateRepo(repo, new ECKey[] {k, k2, k3, k4, k5});

        repo.createAccount(newAddress8);
        repo.createAccount(newAddress6);

        // create ANS contracts, contract1  will be valid parent domain, contract2 will not be
        AionNameServiceContract ansc1 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k2.getAddress()));
        AionNameServiceContract ansc3 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress3, Address.wrap(k3.getAddress()));

        // format inputs and execute
        byte[] combined2 =
                setupInputs(
                        newAddress8,
                        (byte) 0x0,
                        (byte) 0x4,
                        k2,
                        domainAddress4,
                        domainName2,
                        domainName4);
        ContractExecutionResult res2 = ansc2.execute(combined2, inputEnergy);
        byte[] combined3 =
                setupInputs(
                        newAddress8,
                        (byte) 0x0,
                        (byte) 0x4,
                        k3,
                        domainAddress5,
                        domainName3,
                        domainName5);
        ContractExecutionResult res3 = ansc3.execute(combined3, inputEnergy);
        byte[] combined4 = setupInputs(newAddress7, (byte) 0x0, (byte) 0x1, k3);
        ContractExecutionResult res4 = ansc3.execute(combined4, inputEnergy);

        // check for pass and fails
        assertEquals(ResultCode.INTERNAL_ERROR, res2.getCode());
        assertEquals(0L, res2.getNrgLeft());
        assertEquals(ResultCode.SUCCESS, res3.getCode());
        assertEquals(3000L, res3.getNrgLeft());
        assertEquals(ResultCode.SUCCESS, res4.getCode());
        assertEquals(4000L, res4.getNrgLeft());

        AionNameServiceContract ansc4 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress4, Address.wrap(k4.getAddress()));
        // storage checks
        assertEquals(Address.wrap(k4.getAddress()), ansc4.getOwnerAddress());

        byte[] combined =
                setupInputs(
                        newAddress6,
                        (byte) 0x0,
                        (byte) 0x4,
                        k,
                        domainAddress5,
                        domainName1,
                        domainName5);
        ContractExecutionResult res = ansc1.execute(combined, inputEnergy);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(3000L, res.getNrgLeft());
    }

    @Test
    public void bigTest() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});
        repo.createAccount(newAddress3);
        repo.createAccount(newAddress5);

        // create ANS contracts
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k.getAddress()));

        // setup the inputs 1: set
        // resolver-----------------------------------------------------------------------------
        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x1, k);

        // setup the inputs 2: set TTL
        byte[] combined2 = setupInputs(newAddress2, (byte) 0x0, (byte) 0x2, k);

        // setup the inputs 3: Transfer(set) owner
        byte[] combined3 = setupInputs(newAddress3, (byte) 0x0, (byte) 0x3, k);

        // setup the inputs 4: set Resolver for ansc2
        byte[] combined4 = setupInputs(newAddress4, (byte) 0x0, (byte) 0x1, k);

        // setup the inputs 5: Transfer(set) owner
        byte[] combined5 = setupInputs(newAddress5, (byte) 0x0, (byte) 0x3, k);

        // execute contract
        ContractExecutionResult res1 = ansc.execute(combined, inputEnergy);
        ContractExecutionResult res2 = ansc.execute(combined2, inputEnergy);
        ContractExecutionResult res3 = ansc.execute(combined3, inputEnergy);

        // basic checks
        // ------------------------------------------------------------------------------------------------
        assertEquals(ResultCode.SUCCESS, res1.getCode());
        assertEquals(4000L, res1.getNrgLeft());
        assertEquals(ResultCode.SUCCESS, res2.getCode());
        assertEquals(4000L, res2.getNrgLeft());
        assertEquals(ResultCode.SUCCESS, res3.getCode());
        assertEquals(3000L, res3.getNrgLeft());

        // storage checks
        assertEquals(newAddress1, ansc.getResolverAddress());
        assertEquals(newAddress2, ansc.getTTL());
        assertEquals(newAddress3, ansc.getOwnerAddress());

        // contract2
        ContractExecutionResult res4 = ansc2.execute(combined4, inputEnergy);
        assertEquals(ResultCode.SUCCESS, res4.getCode());
        assertEquals(4000L, res4.getNrgLeft());
        assertEquals(newAddress4, ansc2.getResolverAddress());

        // contract1 transfer owner, and checks if transfer is correctly executed
        ContractExecutionResult res5 = ansc.execute(combined5, inputEnergy);
        assertEquals(ResultCode.SUCCESS, res5.getCode());
        assertEquals(3000L, res5.getNrgLeft());
        assertEquals(newAddress5, ansc.getOwnerAddress());
    }

    /**
     * Set contract2 and contract3 as a subdomain of contract1 Through contract1, change owner of
     * contract 2 from Address4 to Address 5
     *
     * <p>Initial set up: After Transfer: Storage: contract1: domainAddress1 Storage: contract1:
     * domainAddress1 resolver - newAddress1 resolver - newAddress1 ttl - newAddress2 ttl -
     * newAddress2 owner - newAddress3 owner - newAddress3 contract2: domainAddress2 contract2:
     * domainAddress2 resolver - resolver - ttl - ttl - owner - newAddress4 owner - newAddress5
     * contract3: domainAddress3 (ttc) contract3: domainAddress3 (ttc) resolver - resolver - ttl -
     * ttl - owner - newAddress6 owner - newAddress7
     */
    @Test
    public void testTransferSubdomainOwnership() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});
        repo.createAccount(newAddress5);

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k.getAddress()));

        // put original owner address
        storeValueToRepo(repo, domainAddress2, ownerHash1, ownerHash2, newAddress4);

        byte[] combined =
                setupInputs(
                        newAddress5,
                        (byte) 0x0,
                        (byte) 0x4,
                        k,
                        domainAddress2,
                        domainName1,
                        domainName2);
        // change subdomain owner address
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress =
                ansc2.getOwnerAddress(Address.wrap(combineTwoBytes(ownerHash1, ownerHash2)));

        // check for success and failure
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(newAddress5, actualReturnedAddress);
    }

    @Test
    public void testSetResolver() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 4000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        repo.addContract(Address.wrap(k.getAddress()), new byte[100]); // covering contract test

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x1, k);

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(newAddress1, actualReturnedAddress);
    }

    @Test
    public void testNotASubdomain() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});
        repo.createAccount(newAddress5);

        // store values
        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);
        storeValueToRepo(
                repo, domainAddress1, ownerHash1, ownerHash2, Address.wrap(k.getAddress()));
        storeValueToRepo(
                repo, domainAddress2, ownerHash1, ownerHash2, Address.wrap(k.getAddress()));

        // create ans contracts
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k.getAddress()));

        byte[] combined =
                setupInputs(
                        newAddress5,
                        (byte) 0x0,
                        (byte) 0x4,
                        k,
                        domainAddress2,
                        domainName1,
                        notSubdomain);
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress =
                ansc2.getOwnerAddress(Address.wrap(combineTwoBytes(ownerHash1, ownerHash2)));

        // check for success and failure
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(Address.wrap(k.getAddress()), actualReturnedAddress);
    }

    @Test
    public void incorrectInputLength() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x1, k);
        byte[] wrongLength = new byte[130 - 1];
        System.arraycopy(combined, 0, wrongLength, 0, 130 - 1);

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(wrongLength, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testIncorrectSignature() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x1, k);

        // modify the signature in the 110th byte (arbitrarily)
        combined[110] = (byte) (combined[110] + 1);
        for (int i = 34; i < 130; i++) {
            combined[i] = (byte) 0;
        }

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        // since the signature is incorrect, contract is not modified
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testUnsupportedOperation() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 5000;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined =
                setupInputs(
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x6,
                        k); // put (byte) 6 into input as the invalid

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testIncorrectPublicKey() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        ECKey notk = ECKeyFac.inst().create();

        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k, notk});

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x1, notk);

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        // since the signature is incorrect, contract is not modified
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testTransferOwnership() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});
        repo.createAccount(newAddress1);

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x3, k);
        byte[] combined2 = setupInputs(newAddress2, (byte) 0x0, (byte) 0x3, k);

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getOwnerAddress();
        ContractExecutionResult res2 = ansc.execute(combined2, inputEnergy);
        Address actualReturnedAddress2 = ansc.getOwnerAddress();

        // check for success and failure for execute with valid new address
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(newAddress1, actualReturnedAddress);

        // check for success and failure for execute with invalid new address
        assertEquals(ResultCode.INTERNAL_ERROR, res2.getCode());
        assertEquals(inputEnergy, res2.getNrgLeft());
        assertEquals(newAddress1, actualReturnedAddress2);
    }

    @Test
    public void testInsufficientEnergy() {

        // initialize input parameters
        final long inputEnergy = 300L;
        final long expectedEnergyLeft = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress2, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x1, k);
        byte[] combined2 = setupInputs(newAddress2, (byte) 0x0, (byte) 0x2, k);
        byte[] combined3 = setupInputs(newAddress3, (byte) 0x0, (byte) 0x3, k);
        byte[] combined4 =
                setupInputs(
                        newAddress3,
                        (byte) 0x0,
                        (byte) 0x4,
                        k,
                        domainAddress2,
                        domainName1,
                        domainName2);

        // execute ANS contract
        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        ContractExecutionResult res2 = ansc.execute(combined2, inputEnergy);
        ContractExecutionResult res3 = ansc.execute(combined3, inputEnergy);
        ContractExecutionResult res4 = ansc.execute(combined4, inputEnergy);

        Address actualReturnedAddress = ansc.getResolverAddress();
        Address actualReturnedAddress2 = ansc.getTTL();
        Address actualReturnedAddress3 = ansc.getOwnerAddress();
        Address actualReturnedAddress4 = ansc2.getOwnerAddress();

        // check for success and failure
        assertEquals(ResultCode.OUT_OF_NRG, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress);

        // check for success and failure
        assertEquals(ResultCode.OUT_OF_NRG, res2.getCode());
        assertEquals(expectedEnergyLeft, res2.getNrgLeft());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress2);

        // check for success and failure
        assertEquals(ResultCode.OUT_OF_NRG, res3.getCode());
        assertEquals(expectedEnergyLeft, res3.getNrgLeft());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress3);

        // check for success and failure
        assertEquals(ResultCode.OUT_OF_NRG, res4.getCode());
        assertEquals(expectedEnergyLeft, res4.getNrgLeft());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress4);
    }

    @Test
    public void testSubdomainDoesNotExist() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;
        final long expectedEnergyLeft2 = 0L;

        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = new DummyRepo();
        createAccounts(repo, new ECKey[] {k});
        repo.createAccount(newAddress1);
        repo.createAccount(newAddress2);

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        (IRepositoryCache) repo, domainAddress1, Address.wrap(k.getAddress()));

        byte[] combined = setupInputs(newAddress1, (byte) 0x0, (byte) 0x3, k);
        byte[] combined2 =
                setupInputs(
                        newAddress2,
                        (byte) 0x0,
                        (byte) 0x4,
                        k,
                        domainAddress2,
                        domainName1,
                        notSubdomain);

        ContractExecutionResult res = ansc.execute(combined, inputEnergy);
        ContractExecutionResult res2 = ansc.execute(combined2, inputEnergy);

        // check for success and failure
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(expectedEnergyLeft, res.getNrgLeft());
        assertEquals(ResultCode.INTERNAL_ERROR, res2.getCode());
        assertEquals(expectedEnergyLeft2, res2.getNrgLeft());
    }

    /** Helper functions for setup, conversion, and storage */
    private byte[] setupInputs(Address newAddress, byte id, byte operation, ECKey k) {
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put(id) // chainID
                .put(operation) // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        return bb.array();
    }

    private byte[] setupInputs(
            Address newAddress,
            byte id,
            byte operation,
            ECKey k,
            Address subdomainAddress,
            String domainName,
            String subdomainName) {
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put(id) // chainID
                .put(operation) // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(payload);

        bb = ByteBuffer.allocate(34 + 96 + 32 + 32 + 32);
        bb.put(payload);
        bb.put(signature.toBytes());
        bb.put(subdomainAddress.toBytes());

        byte[] name1 = new byte[32];
        byte[] name2 = new byte[32];

        try {
            System.arraycopy(domainName.getBytes("UTF-8"), 0, name1, 0, domainName.length());
            System.arraycopy(subdomainName.getBytes("UTF-8"), 0, name2, 0, subdomainName.length());
        } catch (UnsupportedEncodingException a) {
            return null;
        }

        bb.put(name1);
        bb.put(name2);
        return bb.array();
    }

    private byte[] combineTwoBytes(byte[] byte1, byte[] byte2) {
        byte[] combined = new byte[32];
        System.arraycopy(byte1, 0, combined, 0, 16);
        System.arraycopy(byte2, 0, combined, 16, 16);
        return combined;
    }

    /**
     * put some data into the database for testing
     *
     * <p>aion aion.aion aion.bion aion.bion.cion aion.bion.dion
     */
    private DummyRepo populateRepo() {
        DummyRepo repo = new DummyRepo();

        byte[] resolverHash1 = blake128(RESOLVER_HASH.getBytes());
        byte[] resolverHash2 = blake128(resolverHash1);

        byte[] TTLHash1 = blake128(TTL_HASH.getBytes());
        byte[] TTLHash2 = blake128(TTLHash1);

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        storeValueToRepo(repo, domainAddress1, resolverHash1, resolverHash2, newAddress1);
        storeValueToRepo(repo, domainAddress1, TTLHash1, TTLHash2, newAddress2);
        storeValueToRepo(repo, domainAddress1, ownerHash1, ownerHash2, newAddress3);

        storeValueToRepo(repo, domainAddress2, ownerHash1, ownerHash2, newAddress4);
        storeValueToRepo(repo, domainAddress3, ownerHash1, ownerHash2, newAddress5);
        storeValueToRepo(repo, domainAddress4, ownerHash1, ownerHash2, newAddress6);
        storeValueToRepo(repo, domainAddress5, ownerHash1, ownerHash2, newAddress7);
        return repo;
    }

    private DummyRepo populateRepo(DummyRepo repo, ECKey[] keys) {

        byte[] resolverHash1 = blake128(RESOLVER_HASH.getBytes());
        byte[] resolverHash2 = blake128(resolverHash1);

        byte[] TTLHash1 = blake128(TTL_HASH.getBytes());
        byte[] TTLHash2 = blake128(TTLHash1);

        byte[] ownerHash1 = blake128(OWNER_HASH.getBytes());
        byte[] ownerHash2 = blake128(ownerHash1);

        storeValueToRepo(repo, domainAddress1, resolverHash1, resolverHash2, newAddress1);
        storeValueToRepo(repo, domainAddress1, TTLHash1, TTLHash2, newAddress2);
        storeValueToRepo(
                repo, domainAddress1, ownerHash1, ownerHash2, Address.wrap(keys[0].getAddress()));

        storeValueToRepo(
                repo, domainAddress2, ownerHash1, ownerHash2, Address.wrap(keys[1].getAddress()));
        storeValueToRepo(
                repo, domainAddress3, ownerHash1, ownerHash2, Address.wrap(keys[2].getAddress()));
        storeValueToRepo(
                repo, domainAddress4, ownerHash1, ownerHash2, Address.wrap(keys[3].getAddress()));
        storeValueToRepo(
                repo, domainAddress5, ownerHash1, ownerHash2, Address.wrap(keys[4].getAddress()));
        return repo;
    }

    private void storeValueToRepo(
            DummyRepo repo, Address domainAddress, byte[] hash1, byte[] hash2, Address value) {
        byte[] combined = value.toBytes();
        byte[] value1 = new byte[16];
        byte[] value2 = new byte[16];
        System.arraycopy(combined, 0, value1, 0, 16);
        System.arraycopy(combined, 16, value2, 0, 16);
        storeValueToRepo(repo, domainAddress, hash1, hash2, value1, value2);
    }

    private void storeValueToRepo(
            DummyRepo repo,
            Address domainAddress,
            byte[] hash1,
            byte[] hash2,
            byte[] value1,
            byte[] value2) {
        repo.addStorageRow(domainAddress, new DataWord(hash1), new DataWord(value1));
        repo.addStorageRow(domainAddress, new DataWord(hash2), new DataWord(value2));
    }

    private void createAccounts(DummyRepo repository, ECKey[] accountList) {
        for (ECKey key : accountList) repository.createAccount(Address.wrap(key.getAddress()));
    }
}
