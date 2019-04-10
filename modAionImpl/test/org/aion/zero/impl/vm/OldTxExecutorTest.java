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

package org.aion.zero.impl.vm;

import static org.aion.mcf.tx.TransactionTypes.FVM_CREATE_CODE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.crypto.ECKeyFac;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.solidity.Compiler.Options;
import org.aion.types.Address;
import org.aion.util.conversions.Hex;
import org.aion.vm.BulkExecutor;
import org.aion.vm.ExecutionBatch;
import org.aion.vm.PostExecutionWork;
import org.aion.vm.VirtualMachineProvider;
import org.aion.vm.exception.VMException;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * These were the tests previous in TransactionExecutorUnitTest but that class is now strictly unit
 * testing. These tests are more on the integration test level. Once there's a place to fit these
 * they will be moved.
 */
public class OldTxExecutorTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();
        blockchain = bundle.bc;

        if (VirtualMachineProvider.isMachinesAreLive()) {
            return;
        }
        VirtualMachineProvider.initializeAllVirtualMachines();
    }

    @After
    public void tearDown() {
        blockchain = null;
        if (VirtualMachineProvider.isMachinesAreLive()) {
            VirtualMachineProvider.shutdownAllVirtualMachines();
        }
    }

    @Test
    public void testCallTransaction() throws IOException, VMException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = DataWordImpl.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to =
                Address.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWordImpl.ZERO.getData();
        byte[] data = Hex.decode("c0004213");
        long nrg = new DataWordImpl(100000L).longValue();
        long nrgPrice = DataWordImpl.ONE.longValue();
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repo = blockchain.getRepository();
        RepositoryCache cache = repo.startTracking();
        cache.addBalance(from, BigInteger.valueOf(100_000).multiply(tx.nrgPrice().value()));
        cache.createAccount(to);
        cache.saveCode(to, Hex.decode(contract));
        cache.saveVmType(to, FVM_CREATE_CODE);

        cache.flush();

        ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
        BulkExecutor exec =
                new BulkExecutor(
                        details,
                        repo.startTracking(),
                        false,
                        true,
                        block.getNrgLimit(),
                        LOGGER_VM,
                        getPostExecutionWork());
        AionTxReceipt receipt = exec.execute().get(0).getReceipt();
        System.out.println(receipt);

        assertArrayEquals(
                Hex.decode("00000000000000000000000000000000"), receipt.getTransactionOutput());
    }

    @Test
    public void testCreateTransaction() throws IOException, VMException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin;
        System.out.println(deployer);

        byte[] txNonce = DataWordImpl.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to = null;
        byte[] value = DataWordImpl.ZERO.getData();
        byte[] data = Hex.decode(deployer);
        long nrg = 500_000L;
        long nrgPrice = 1;
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repoTop = blockchain.getRepository();
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = repoTop.startTracking();
        repo.addBalance(from, BigInteger.valueOf(500_000L).multiply(tx.nrgPrice().value()));

        ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
        BulkExecutor exec =
                new BulkExecutor(
                        details,
                        repo.startTracking(),
                        false,
                        true,
                        block.getNrgLimit(),
                        LOGGER_VM,
                        getPostExecutionWork());
        AionTxReceipt receipt = exec.execute().get(0).getReceipt();
        System.out.println(receipt);

        assertArrayEquals(
                Hex.decode(deployer.substring(deployer.indexOf("60506040", 1))),
                receipt.getTransactionOutput());
    }

    @Test
    public void testPerformance() throws IOException, VMException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = DataWordImpl.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to =
                Address.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWordImpl.ZERO.getData();
        byte[] data = Hex.decode("c0004213");
        long nrg = new DataWordImpl(100000L).longValue();
        long nrgPrice = DataWordImpl.ONE.longValue();
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);
        tx.sign(ECKeyFac.inst().create());

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repoTop = blockchain.getRepository();
        RepositoryCache repo = repoTop.startTracking();
        repo.addBalance(from, BigInteger.valueOf(100_000).multiply(tx.nrgPrice().value()));
        repo.createAccount(to);
        repo.saveCode(to, Hex.decode(contract));
        repo.saveVmType(to, FVM_CREATE_CODE);
        repo.flush();

        long t1 = System.nanoTime();
        long repeat = 1000;
        for (int i = 0; i < repeat; i++) {
            ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
            BulkExecutor exec =
                    new BulkExecutor(
                            details,
                            repo.startTracking(),
                            false,
                            true,
                            block.getNrgLimit(),
                            LOGGER_VM,
                            getPostExecutionWork());
            exec.execute();
        }
        long t2 = System.nanoTime();
        System.out.println((t2 - t1) / repeat);
    }

    @Test
    public void testBasicTransactionCost() throws VMException {
        byte[] txNonce = DataWordImpl.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to =
                Address.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWordImpl.ONE.getData();
        byte[] data = new byte[0];
        long nrg = new DataWordImpl(100000L).longValue();
        long nrgPrice = DataWordImpl.ONE.longValue();
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repoTop = blockchain.getRepository();
        RepositoryCache repo = repoTop.startTracking();
        repo.addBalance(from, BigInteger.valueOf(1_000_000_000L));
        repo.flush();

        ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
        BulkExecutor exec =
                new BulkExecutor(
                        details,
                        repo.startTracking(),
                        false,
                        true,
                        block.getNrgLimit(),
                        LOGGER_VM,
                        getPostExecutionWork());
        AionTxReceipt receipt = exec.execute().get(0).getReceipt();
        System.out.println(receipt);

        assertEquals(tx.transactionCost(block.getNumber()), receipt.getEnergyUsed());
    }

    private static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(Address.SIZE);
        byte[] logsBloom = new byte[0];
        byte[] difficulty = new DataWordImpl(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[0];

        // TODO: set a dummy limit of 5000000 for now
        return new AionBlock(
                parentHash,
                Address.wrap(coinbase),
                logsBloom,
                difficulty,
                number,
                timestamp,
                extraData,
                nonce,
                receiptsRoot,
                transactionsRoot,
                stateRoot,
                transactionsList,
                solutions,
                0,
                5000000);
    }

    private PostExecutionWork getPostExecutionWork() {
        return (r, c, s, t, b) -> {
            return 0L;
        };
    }
}
