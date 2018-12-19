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

package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.ImportResult;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Ignore;
import org.junit.Test;

public class BlockchainAccountStateTest {

    /** Test the effects of growing state of a single account */
    @Ignore
    @Test
    public void testAccountState() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();

        StandaloneBlockchain bc = bundle.bc;

        ECKey senderKey = bundle.privateKeys.get(0);

        // send a total of 100 bundles,
        // given the rate we're sending this should give us
        // a 400,000 accounts (not counting the 10 pre-generated for us)
        AionBlock previousBlock = bc.genesis;
        for (int i = 0; i < 1000; i++) {
            previousBlock = createBundleAndCheck(bc, senderKey, previousBlock);
        }
    }

    private static final byte[] ZERO_BYTE = new byte[0];

    private static AionBlock createBundleAndCheck(
            StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        BigInteger accountNonce = bc.getRepository().getNonce(new AionAddress(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 400 transactions per bundle
        // byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice
        for (int i = 0; i < 400; i++) {
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
        assertThat(block.getTransactionsList().size()).isEqualTo(400);
        // clear the trie
        bc.getRepository().flush();

        long startTime = System.nanoTime();
        ImportResult result = bc.tryToConnect(block);
        long endTime = System.nanoTime();
        System.out.println("processing time: " + (endTime - startTime) + " ns");

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        return block;
    }

    private static final String STATE_EXPANSION_BYTECODE =
            "0x605060405260006001600050909055341561001a5760006000fd5b61001f565b6101688061002e6000396000f30060506040526000356c01000000000000000000000000900463ffffffff16806331e658a514610049578063549262ba1461008957806361bc221a1461009f57610043565b60006000fd5b34156100555760006000fd5b610073600480808060100135903590916020019091929050506100c9565b6040518082815260100191505060405180910390f35b34156100955760006000fd5b61009d6100eb565b005b34156100ab5760006000fd5b6100b3610133565b6040518082815260100191505060405180910390f35b6000600050602052818160005260105260306000209050600091509150505481565b6001600060005060006001600050546000825281601001526020019081526010016000209050600050819090905550600160008181505480929190600101919050909055505b565b600160005054815600a165627a7a72305820c615f3373321aa7e9c05d9a69e49508147861fb2a54f2945fbbaa7d851125fe80029";
    /** Test the effects of expanding our account storage beyond */
    @Ignore
    @Test
    public void testExpandAccountStorage() {
        // manually deploy the contract bytecode
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();

        StandaloneBlockchain bc = bundle.bc;

        ECKey senderKey = bundle.privateKeys.get(0);

        // deploy the contract

        // send a total of 100 bundles,
        // given the rate we're sending this should give us
        // a 400,000 accounts (not counting the 10 pre-generated for us)
        AionBlock previousBlock = bc.genesis;
        for (int i = 0; i < 1000; i++) {
            previousBlock = createBundleAndCheck(bc, senderKey, previousBlock);
        }
    }

    private static AionBlock createContractBundle(
            StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        BigInteger accountNonce = bc.getRepository().getNonce(new AionAddress(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 400 transactions per bundle
        // byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice
        for (int i = 0; i < 400; i++) {
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
        }

        AionBlock block = bc.createNewBlock(parentBlock, transactions, true);
        // clear the trie
        bc.getRepository().flush();

        long startTime = System.nanoTime();
        ImportResult result = bc.tryToConnect(block);
        long endTime = System.nanoTime();
        System.out.println("processing time: " + (endTime - startTime) + " ns");

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        return block;
    }
}
