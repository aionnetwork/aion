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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.aion.base.type.AionAddress;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;

/**
 * Methods used by several classes testing the blockchain functionality.
 *
 * @author Alexandra Roatis
 */
public class BlockchainTestUtils {
    private static final Random rand = new Random();
    private static final byte[] ZERO_BYTE = new byte[0];
    private static final long NRG = 21000L;
    private static final long NRG_PRICE = 1L;

    public static List<ECKey> generateAccounts(int size) {
        List<ECKey> accs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            accs.add(ECKeyFac.inst().create());
        }
        return accs;
    }

    public static List<AionTransaction> generateTransactions(
            int maxSize, List<ECKey> accounts, AionRepositoryImpl repo) {
        int size = rand.nextInt(maxSize);

        if (size == 0) {
            return Collections.emptyList();
        } else {
            // get the current nonce for each account
            Map<ECKey, BigInteger> nonces = new HashMap<>();
            for (ECKey key : accounts) {
                nonces.put(key, repo.getNonce(new AionAddress(key.getAddress())));
            }

            List<AionTransaction> transactions = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                // get a random account
                ECKey key = accounts.get(rand.nextInt(accounts.size()));
                BigInteger accountNonce = nonces.get(key);

                // generate a random address
                AionAddress destAddr = new AionAddress(HashUtil.h256(accountNonce.toByteArray()));
                AionTransaction newTx =
                        new AionTransaction(
                                accountNonce.toByteArray(),
                                destAddr,
                                BigInteger.ONE.toByteArray(),
                                ZERO_BYTE,
                                NRG,
                                NRG_PRICE);
                newTx.sign(key);
                transactions.add(newTx);
                accountNonce = accountNonce.add(BigInteger.ONE);
                nonces.put(key, accountNonce);
            }

            return transactions;
        }
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param blocks number of blocks in the chain
     * @param frequency every multiple of frequency block will be on the main chain
     * @param accounts existing accounts
     * @param txCount maximum number of transactions per block
     */
    public static void generateRandomChain(
            StandaloneBlockchain chain,
            int blocks,
            int frequency,
            List<ECKey> accounts,
            int txCount) {

        AionBlock parent, block, mainChain;
        mainChain = chain.getGenesis();

        List<AionBlock> knownBlocks = new ArrayList<>();
        knownBlocks.add(mainChain);

        List<AionTransaction> txs;
        AionRepositoryImpl repo = chain.getRepository();

        long time = System.currentTimeMillis();
        for (int i = 0; i < blocks; i++) {

            // ensuring that we add to the main chain at least every MAIN_CHAIN_FREQUENCY block
            if (i % frequency == 0) {
                // the parent will be the main chain
                parent = mainChain;
            } else {
                // decide if to keep the node among potential parents
                if (rand.nextBoolean()) {
                    // the parent is a random already imported block
                    parent = knownBlocks.get(rand.nextInt(knownBlocks.size()));
                } else {
                    // randomly remove some parent nodes
                    parent = knownBlocks.remove(rand.nextInt(knownBlocks.size()));
                }
            }

            // generate transactions for correct root
            byte[] originalRoot = repo.getRoot();
            repo.syncToRoot(parent.getStateRoot());
            txs = generateTransactions(txCount, accounts, repo);
            repo.syncToRoot(originalRoot);

            block = chain.createNewBlockInternal(parent, txs, true, time / 10000L).block;
            block.setExtraData(String.valueOf(i).getBytes());

            ImportResult result = chain.tryToConnectInternal(block, (time += 10));
            knownBlocks.add(block);
            if (result == ImportResult.IMPORTED_BEST) {
                mainChain = block;
            }

            System.out.format(
                    "Created block with hash: %s, number: %6d, extra data: %6s, txs: %3d, import status: %20s %n",
                    block.getShortHash(),
                    block.getNumber(),
                    new String(block.getExtraData()),
                    block.getTransactionsList().size(),
                    result.toString());
        }
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param blocks number of blocks in the chain
     * @param frequency every multiple of frequency block will be on the main chain
     */
    public static void generateRandomChainWithoutTransactions(
            StandaloneBlockchain chain, int blocks, int frequency) {

        AionBlock parent, block, mainChain;
        mainChain = chain.getGenesis();

        List<AionBlock> knownBlocks = new ArrayList<>();
        knownBlocks.add(mainChain);

        List<AionTransaction> txs = Collections.emptyList();

        long time = System.currentTimeMillis();
        for (int i = 0; i < blocks; i++) {

            // ensuring that we add to the main chain at least every MAIN_CHAIN_FREQUENCY block
            if (i % frequency == 0) {
                // the parent will be the main chain
                parent = mainChain;
            } else {
                // decide if to keep the node among potential parents
                if (rand.nextBoolean()) {
                    // the parent is a random already imported block
                    parent = knownBlocks.get(rand.nextInt(knownBlocks.size()));
                } else {
                    // randomly remove some parent nodes
                    parent = knownBlocks.remove(rand.nextInt(knownBlocks.size()));
                }
            }

            block = chain.createNewBlockInternal(parent, txs, true, time / 10000L).block;
            block.setExtraData(String.valueOf(i).getBytes());

            ImportResult result = chain.tryToConnectInternal(block, (time += 10));
            knownBlocks.add(block);
            if (result == ImportResult.IMPORTED_BEST) {
                mainChain = block;
            }

            System.out.format(
                    "Created block with hash: %s, number: %6d, extra data: %6s, txs: %3d, import status: %20s %n",
                    block.getShortHash(),
                    block.getNumber(),
                    new String(block.getExtraData()),
                    block.getTransactionsList().size(),
                    result.toString());
        }
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param accounts existing accounts
     * @param txCount maximum number of transactions per block
     */
    public static AionBlock generateNextBlock(
            StandaloneBlockchain chain, List<ECKey> accounts, int txCount) {

        AionBlock block, parent = chain.getBestBlock();
        AionRepositoryImpl repo = chain.getRepository();
        List<AionTransaction> txs = generateTransactions(txCount, accounts, repo);

        long time = System.currentTimeMillis();
        block = chain.createNewBlockInternal(parent, txs, true, time / 10000L).block;
        block.setExtraData(String.valueOf(time).getBytes());
        return block;
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param parent the parent block for the newly generated block
     * @param accounts existing accounts
     * @param txCount maximum number of transactions per block
     * @implNote returns {@code null} if the parent block is not part of the chain
     */
    public static AionBlock generateNewBlock(
            StandaloneBlockchain chain, AionBlock parent, List<ECKey> accounts, int txCount) {
        if (!chain.getBlockStore().isBlockExist(parent.getHash())) return null;

        AionBlock block;
        AionRepositoryImpl repo = chain.getRepository();
        List<AionTransaction> txs;

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        txs = generateTransactions(txCount, accounts, repo);
        repo.syncToRoot(originalRoot);

        long time = System.currentTimeMillis();
        block = chain.createNewBlockInternal(parent, txs, true, time / 10000L).block;
        block.setExtraData(String.valueOf(time).getBytes());
        return block;
    }
}
