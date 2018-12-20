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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.aion.crypto.HashUtil.blake128;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class AionNameServiceContractTest {
    private static final String RESOLVER_HASH = "ResolverHash";
    private static final String OWNER_HASH = "OwnerHash";
    private static final String TTL_HASH = "TTLHash";

    private String domainName1 = "aion";
    private String domainName2 = "aion.aion"; // subdomain of domainName1
    private String domainName3 = "bion.aion"; // subdomain of domainName1
    private String domainName4 = "cion.bion.aion"; // subdomain of domainName1 and domainName3
    private String domainName5 = "dion.bion.aion"; // subdomain of domainName1 and domainName3
    private String domainName6 = "aion.aion.aion"; // subdomain of domainName1 and domainName2
    private String notSubdomain = "aion.bion"; // not a subdomain of domainName1

    private static final Address AION =
            AionAddress.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    private Address emptyAddress =
            AionAddress.wrap("0000000000000000000000000000000000000000000000000000000000000000");
    private Address domainAddress1 =
            AionAddress.wrap("a011111111111111111111111111111101010101010101010101010101010101");
    private Address domainAddress2 =
            AionAddress.wrap("a022222222222222222222222222222202020202020202020202020202020202");
    private Address domainAddress3 =
            AionAddress.wrap("a033333333333333333333333333333303030303030303030303030303030303");
    private Address domainAddress4 =
            AionAddress.wrap("a044444444444444444444444444444404040404040404040404040404040404");
    private Address domainAddress5 =
            AionAddress.wrap("a055555555555555555555555555555505050505050050505050505050505050");
    private Address domainAddress6 =
            AionAddress.wrap("a066666666666666666666666666666606060606060606060606060606060060");
    private Address invalidDomainAddress =
            AionAddress.wrap("b066666666666666666666666666666606060606060606060606060606060606");

    private Address newAddress1 =
            AionAddress.wrap("1000000000000000000000000000000000000000000000000000000000000001");
    private Address newAddress2 =
            AionAddress.wrap("0100000000000000000000000000000000000000000000000000000000000010");
    private Address newAddress3 =
            AionAddress.wrap("0010000000000000000000000000000000000000000000000000000000000100");
    private Address newAddress4 =
            AionAddress.wrap("0001000000000000000000000000000000000000000000000000000000001000");
    private Address newAddress5 =
            AionAddress.wrap("0000100000000000000000000000000000000000000000000000000000010000");
    private Address newAddress6 =
            AionAddress.wrap("0000010000000000000000000000000000000000000000000000000000100000");
    private Address newAddress7 =
            AionAddress.wrap("0000001000000000000000000000000000000000000000000000000001000000");
    private Address newAddress8 =
            AionAddress.wrap("0000000100000000000000000000000000000000000000000000000010000000");

    private IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo;
    private ECKey defaultKey;
    private ECKey defaultKey2;
    private ECKey k;
    private ECKey k2;
    private ECKey k3;
    private ECKey k4;
    private ECKey k5;
    private Address defaultAddress;
    private Address defaultAddress2;
    private long DEFAULT_INPUT_NRG = 24000;
    static IBlockchain blockchain;

    @BeforeClass
    public static void setupBlockChain() throws InterruptedException {
        // create standalone blockchain for testing
        createBlockchain(2, TimeUnit.SECONDS.toMillis(1));
    }

    @Before
    public void setup() {
        repo = populateRepo();
        defaultKey = ECKeyFac.inst().create();
        defaultKey2 = ECKeyFac.inst().create();
        repo.createAccount(AionAddress.wrap(defaultKey.getAddress()));
        repo.createAccount(AionAddress.wrap(defaultKey2.getAddress()));
        repo.addBalance(AionAddress.wrap(defaultKey.getAddress()), new BigInteger("10000"));
        repo.addBalance(AionAddress.wrap(defaultKey2.getAddress()), new BigInteger("10000"));

        k = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();
        k4 = ECKeyFac.inst().create();
        k5 = ECKeyFac.inst().create();
        repo.createAccount(AionAddress.wrap(k.getAddress()));
        repo.createAccount(AionAddress.wrap(k2.getAddress()));
        repo.createAccount(AionAddress.wrap(k3.getAddress()));
        repo.createAccount(AionAddress.wrap(k4.getAddress()));
        repo.createAccount(AionAddress.wrap(k5.getAddress()));
        repo.addBalance(AionAddress.wrap(k.getAddress()), new BigInteger("10000"));
        repo.addBalance(AionAddress.wrap(k2.getAddress()), new BigInteger("10000"));
        repo.addBalance(AionAddress.wrap(k3.getAddress()), new BigInteger("10000"));
        repo.addBalance(AionAddress.wrap(k4.getAddress()), new BigInteger("10000"));

        BigInteger amount = new BigInteger("1000");
        byte[] combined =
                setupInputs(
                        domainName2,
                        AionAddress.wrap(defaultKey.getAddress()),
                        amount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result = aac.execute(combined, 24000);

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BigInteger amount2 = new BigInteger("2000");
        byte[] combined2 =
                setupInputs(
                        domainName3,
                        AionAddress.wrap(defaultKey2.getAddress()),
                        amount2.toByteArray(),
                        defaultKey2);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result2 = aac2.execute(combined2, 24000);

        // wait for the domain to become active,
        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        defaultAddress = AionAddress.wrap(result.getReturnData());
        defaultAddress2 = AionAddress.wrap(result2.getReturnData());
    }

    @After
    public void tearDown() {
        AionNameServiceContract.clearDomainList();
    }

    @Test // try to create errors
    public void testInvalidValues() {
        final long inputEnergy = 5000L;
        repo.createAccount(newAddress2);
        repo.createAccount(newAddress1);

        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        repo, defaultAddress2, AionAddress.wrap(defaultKey2.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x4,
                        defaultKey,
                        newAddress3,
                        "ai" + "on",
                        "aion.aion");
        byte[] combined2 =
                setupInputs(
                        AionAddress.wrap(defaultKey2.getAddress()),
                        newAddress2,
                        (byte) 0x0,
                        (byte) 0x4,
                        defaultKey2,
                        newAddress3,
                        "aion",
                        "aion.aion");

        // trying to access domain with wrong address
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        PrecompiledTransactionResult res2 = ansc2.execute(combined2, inputEnergy);

        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(3000L, res.getEnergyRemaining());

        assertEquals(PrecompiledResultCode.FAILURE, res2.getResultCode());
        assertEquals(0, res2.getEnergyRemaining());
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
                        repo, invalidDomainAddress, AionAddress.wrap(k.getAddress()));
        assertNull(ansc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidDomainOwnerAddress() {
        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = populateRepo();
        createAccounts(repo, new ECKey[] {k});

        // The owner address need to exist in the given repoistory, as an account or smart contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(repo, domainAddress2, newAddress1);
        assertNull(ansc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConflictOwnerAddress() {
        ECKey k = ECKeyFac.inst().create();
        DummyRepo repo = populateRepo();
        createAccounts(repo, new ECKey[] {k});

        // check that the given owner address is the same as the owner address in the repository.
        AionNameServiceContract ansc =
                new AionNameServiceContract(repo, domainAddress2, AionAddress.wrap(k.getAddress()));
        assertNull(ansc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnavailableDomain() {
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, domainAddress6, AionAddress.wrap(defaultKey.getAddress()));
        assertNull(ansc);
    }

    @Test
    public void testGetNameAndAddress() {
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));
        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        repo, defaultAddress2, AionAddress.wrap(defaultKey2.getAddress()));

        assertEquals(domainName2, ansc.getRegisteredDomainName(defaultAddress));
        assertEquals(domainName3, ansc.getRegisteredDomainName(defaultAddress2));

        assertEquals(defaultAddress, ansc.getRegisteredDomainAddress(domainName2));
        assertEquals(defaultAddress2, ansc.getRegisteredDomainAddress(domainName3));

        assertNull(ansc.getRegisteredDomainName(domainAddress5));
        assertNull(ansc.getRegisteredDomainAddress(domainName5));
    }

    @Test
    public void testTransferSubdomainOwnership() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        repo.createAccount(newAddress5);

        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress5,
                        (byte) 0x0,
                        (byte) 0x4,
                        defaultKey,
                        defaultAddress2,
                        domainName2,
                        domainName6);
        // change subdomain owner address
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);

        // check for success and failure
        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
    }

    @Test
    public void testSetResolver() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 4000L;

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x1,
                        defaultKey);

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        assertEquals(newAddress1, actualReturnedAddress);
    }

    @Test
    public void testSetTTL() {

        // initilize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 4000L;

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x2,
                        defaultKey);

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getTTL();

        // check for success and failure
        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        assertEquals(newAddress1, actualReturnedAddress);
    }

    @Test
    public void testNotASubdomain() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0;
        ;
        repo.createAccount(newAddress5);

        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        newAddress5,
                        (byte) 0x0,
                        (byte) 0x4,
                        defaultKey,
                        domainAddress2,
                        domainName2,
                        notSubdomain);
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);

        // check for success and failure
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
    }

    @Test
    public void incorrectInputLength() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        // create ans contracts
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x1,
                        defaultKey);
        byte[] wrongLength = new byte[130 - 1];
        System.arraycopy(combined, 0, wrongLength, 0, 130 - 1);

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(wrongLength, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testIncorrectSignature() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x1,
                        defaultKey);

        // modify the signature in the 110th byte (arbitrarily)
        combined[110] = (byte) (combined[110] + 1);
        for (int i = 34; i < 130; i++) {
            combined[i] = (byte) 0;
        }

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        // since the signature is incorrect, contract is not modified
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testUnsupportedOperation() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 5000;

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x6,
                        defaultKey); // put (byte) 6 into input as the invalid

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testIncorrectPublicKey() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 0L;

        // ECKey k = ECKeyFac.inst().create();
        ECKey notk = ECKeyFac.inst().create();

        repo.createAccount(AionAddress.wrap(notk.getAddress()));

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x1,
                        notk);

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getResolverAddress();

        // check for success and failure
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        // since the signature is incorrect, contract is not modified
        assertEquals(emptyAddress, actualReturnedAddress);
    }

    @Test
    public void testTransferOwnership() {
        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;

        repo.createAccount(newAddress1);

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x3,
                        defaultKey);
        byte[] combined2 =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress2,
                        (byte) 0x0,
                        (byte) 0x3,
                        defaultKey);

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        Address actualReturnedAddress = ansc.getOwnerAddress();
        PrecompiledTransactionResult res2 = ansc.execute(combined2, inputEnergy);
        Address actualReturnedAddress2 = ansc.getOwnerAddress();

        // check for success and failure for execute with valid new address
        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        assertEquals(newAddress1, actualReturnedAddress);

        // check for success and failure for execute with invalid new address
        assertEquals(PrecompiledResultCode.FAILURE, res2.getResultCode());
        assertEquals(inputEnergy, res2.getEnergyRemaining());
        assertEquals(newAddress1, actualReturnedAddress2);
    }

    @Test
    public void testInsufficientEnergy() {

        // initialize input parameters
        final long inputEnergy = 300L;
        final long expectedEnergyLeft = 0L;

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x1,
                        defaultKey);
        byte[] combined2 =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress2,
                        (byte) 0x0,
                        (byte) 0x2,
                        defaultKey);
        byte[] combined3 =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress3,
                        (byte) 0x0,
                        (byte) 0x3,
                        defaultKey);
        byte[] combined4 =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress3,
                        (byte) 0x0,
                        (byte) 0x4,
                        defaultKey,
                        domainAddress2,
                        domainName1,
                        domainName2);

        // execute ANS contract
        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        PrecompiledTransactionResult res2 = ansc.execute(combined2, inputEnergy);
        PrecompiledTransactionResult res3 = ansc.execute(combined3, inputEnergy);
        PrecompiledTransactionResult res4 = ansc.execute(combined4, inputEnergy);

        Address actualReturnedAddress = ansc.getResolverAddress();
        Address actualReturnedAddress2 = ansc.getTTL();
        Address actualReturnedAddress3 = ansc.getOwnerAddress();

        // check for success and failure
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress);

        // check for success and failure
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res2.getResultCode());
        assertEquals(expectedEnergyLeft, res2.getEnergyRemaining());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress2);

        // check for success and failure
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res3.getResultCode());
        assertEquals(expectedEnergyLeft, res3.getEnergyRemaining());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged
        assertEquals(emptyAddress, actualReturnedAddress3);

        // check for success and failure
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res4.getResultCode());
        assertEquals(expectedEnergyLeft, res4.getEnergyRemaining());
        // since there is not enough energy, the contract failed to execute, resolverAddress is
        // unchanged

    }

    @Test
    public void testSubdomainDoesNotExist() {

        // initialize input parameters
        final long inputEnergy = 5000L;
        final long expectedEnergyLeft = 3000L;
        final long expectedEnergyLeft2 = 0L;

        // ECKey k = ECKeyFac.inst().create();
        // DummyRepo repo = new DummyRepo();
        // createAccounts(repo, new ECKey[] {k});
        repo.createAccount(newAddress1);
        repo.createAccount(newAddress2);

        // create ANS contract
        AionNameServiceContract ansc =
                new AionNameServiceContract(
                        repo, defaultAddress, AionAddress.wrap(defaultKey.getAddress()));

        byte[] combined =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress1,
                        (byte) 0x0,
                        (byte) 0x3,
                        defaultKey);
        byte[] combined2 =
                setupInputs(
                        AionAddress.wrap(defaultKey.getAddress()),
                        newAddress2,
                        (byte) 0x0,
                        (byte) 0x4,
                        defaultKey,
                        domainAddress2,
                        domainName2,
                        notSubdomain);

        PrecompiledTransactionResult res = ansc.execute(combined, inputEnergy);
        PrecompiledTransactionResult res2 = ansc.execute(combined2, inputEnergy);

        // check for success and failure
        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(expectedEnergyLeft, res.getEnergyRemaining());
        assertEquals(PrecompiledResultCode.FAILURE, res2.getResultCode());
        assertEquals(expectedEnergyLeft2, res2.getEnergyRemaining());
    }

    @Test()
    public void testANSQuery() {
        ECKey notRegisteredKey = new ECKeyEd25519();

        // wait for setup to pass
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // register multiple domain names
        BigInteger amount = new BigInteger("1000");
        BigInteger amount2 = new BigInteger("2000");
        BigInteger amount3 = new BigInteger("3000");

        byte[] combined =
                setupInputs(
                        "cion.bion.aion",
                        AionAddress.wrap(k.getAddress()),
                        amount.toByteArray(),
                        k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result = aac.execute(combined, DEFAULT_INPUT_NRG);
        Address addr = AionAddress.wrap(result.getReturnData());

        byte[] combined2 =
                setupInputs(
                        "aaaa.aion", AionAddress.wrap(k.getAddress()), amount2.toByteArray(), k);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        aac2.execute(combined2, DEFAULT_INPUT_NRG);

        byte[] combined3 =
                setupInputs(
                        "bbbb.aaaa.aion",
                        AionAddress.wrap(k2.getAddress()),
                        amount3.toByteArray(),
                        k2);
        AionAuctionContract aac3 = new AionAuctionContract(repo, AION, blockchain);
        aac3.execute(combined3, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // register domain for name service
        AionNameServiceContract ansc =
                new AionNameServiceContract(repo, addr, AionAddress.wrap(k.getAddress()));

        System.out.print("");

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ansc.displayRegisteredDomains();
        ansc.displayMyDomains(notRegisteredKey);
        ansc.displayMyDomains(k);
        ansc.displayAllActiveDomains();

        byte[] combined4 =
                setupInputs(
                        "cccc.aaaa.aion",
                        AionAddress.wrap(k.getAddress()),
                        amount3.toByteArray(),
                        k);
        AionAuctionContract aac4 = new AionAuctionContract(repo, AION, blockchain);
        aac4.execute(combined4, DEFAULT_INPUT_NRG);

        byte[] combined5 =
                setupInputs(
                        "cccc.aaaa.aion",
                        AionAddress.wrap(k2.getAddress()),
                        amount2.toByteArray(),
                        k2);
        AionAuctionContract aac5 = new AionAuctionContract(repo, AION, blockchain);
        aac5.execute(combined5, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ansc.displayMyDomains(k);
        ansc.displayAllActiveDomains();
    }

    /** Helper functions for setup, conversion, and storage */
    // for ans basic operation
    private byte[] setupInputs(
            Address ownerAddress, Address newAddress, byte id, byte operation, ECKey k) {
        ByteBuffer bb = ByteBuffer.allocate(34);
        bb.put(id) // chainID
                .put(operation) // OPERATION HERE
                .put(newAddress.toBytes(), 0, 32);

        byte[] payload = bb.array();
        ISignature signature = k.sign(ownerAddress.toBytes());

        bb = ByteBuffer.allocate(34 + 96);
        bb.put(payload);
        bb.put(signature.toBytes());
        return bb.array();
    }

    // for ans subdomain operation
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

    // for auction setup
    private byte[] setupInputs(
            Address ownerAddress,
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
        ISignature signature = k.sign(ownerAddress.toBytes());

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

    // put some data into the database for testing

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
        repo.addStorageRow(
                domainAddress, new DataWord(hash1).toWrapper(), new DataWord(value1).toWrapper());
        repo.addStorageRow(
                domainAddress, new DataWord(hash2).toWrapper(), new DataWord(value2).toWrapper());
    }

    private void createAccounts(DummyRepo repository, ECKey[] accountList) {
        for (ECKey key : accountList) repository.createAccount(AionAddress.wrap(key.getAddress()));
    }

    private byte[] setupInputs(String domainName, Address ownerAddress, byte[] amount, ECKey k) {
        int domainLength = domainName.length();
        int amountLength = amount.length;
        int offset = 0;
        byte[] ret = new byte[1 + domainLength + 32 + 96 + 1 + amountLength];

        ISignature signature = k.sign(ownerAddress.toBytes());

        System.arraycopy(new byte[] {(byte) domainLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(domainName.getBytes(), 0, ret, offset, domainLength);
        offset = offset + domainLength;
        System.arraycopy(ownerAddress.toBytes(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[] {(byte) amountLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amountLength);

        return ret;
    }

    // Creates a new blockchain with numBlocks blocks and sets it to the blockchain field. This
    // method creates a new block every sleepDuration milliseconds.
    private static void createBlockchain(int numBlocks, long sleepDuration)
            throws InterruptedException {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();

        StandaloneBlockchain bc = bundle.bc;
        AionBlock previousBlock = bc.genesis;

        for (int i = 0; i < numBlocks; i++) {
            if (sleepDuration > 0) {
                Thread.sleep(sleepDuration);
            }
            previousBlock = createBundleAndCheck(bc, bundle.privateKeys.get(0), previousBlock);
        }

        blockchain = bc;
    }

    private static AionBlock createBundleAndCheck(
            StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        byte[] ZERO_BYTE = new byte[0];

        BigInteger accountNonce = bc.getRepository().getNonce(new AionAddress(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 100 transactions per bundle
        for (int i = 0; i < 100; i++) {
            Address destAddr = new AionAddress(HashUtil.h256(accountNonce.toByteArray()));
            AionTransaction sendTransaction =
                    new AionTransaction(
                            accountNonce.toByteArray(),
                            destAddr,
                            BigInteger.ONE.toByteArray(),
                            ZERO_BYTE,
                            21000,
                            1);
            sendTransaction.sign(key);
            transactions.add(sendTransaction);
            accountNonce = accountNonce.add(BigInteger.ONE);
        }

        AionBlock block = bc.createNewBlock(parentBlock, transactions, true);
        Assert.assertEquals(100, block.getTransactionsList().size());
        // clear the trie
        bc.getRepository().flush();

        ImportResult result = bc.tryToConnect(block);
        Assert.assertEquals(ImportResult.IMPORTED_BEST, result);
        return block;
    }
}
