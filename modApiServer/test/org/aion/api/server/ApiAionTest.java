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

package org.aion.api.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.undertow.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.SyncInfo;
import org.aion.base.type.AionAddress;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.blockchain.TxResponse;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ApiAionTest {

    private class ApiAionImpl extends ApiAion {

        private boolean onBlockFlag;
        private boolean pendingRcvdFlag;
        private boolean pendingUpdateFlag;

        @Override
        protected void onBlock(AionBlockSummary cbs) {
            onBlockFlag = true;
        }

        @Override
        protected void pendingTxReceived(ITransaction _tx) {
            pendingRcvdFlag = true;
        }

        @Override
        protected void pendingTxUpdate(ITxReceipt _txRcpt, EventTx.STATE _state) {
            pendingUpdateFlag = true;
        }

        private boolean allFlagsSet() {
            return (onBlockFlag && pendingRcvdFlag && pendingUpdateFlag);
        }

        private ApiAionImpl(AionImpl impl) {
            super(impl);
            onBlockFlag = false;
            pendingRcvdFlag = false;
            pendingUpdateFlag = false;
        }

        private void addEvents() {
            EventTx pendingRcvd = new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0);
            AionTransaction tx = new AionTransaction(null);
            List l1 = new ArrayList<ITransaction>();
            l1.add(tx);
            l1.add(tx);
            l1.add(tx);
            pendingRcvd.setFuncArgs(Collections.singletonList(l1));

            ees.add(pendingRcvd);

            EventTx pendingUpdate = new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0);
            List l2 = new ArrayList<>();
            l2.add(new AionTxReceipt());
            l2.add(-1);
            pendingUpdate.setFuncArgs(l2);

            ees.add(pendingUpdate);

            EventBlock evBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
            AionBlockSummary abs = new AionBlockSummary(null, null, null, null);
            evBlock.setFuncArgs(Collections.singletonList(abs));

            ees.add(evBlock);

            // provokes exception in EpApi.run()
            ees.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
            ees.add(new EventDummy());
        }
    }

    private static final String KEYSTORE_PATH;
    private static final String DATABASE_PATH = "ApiServerTestPath";
    private long testStartTime;

    static {
        String storageDir = System.getProperty("local.storage.dir");
        if (storageDir == null || storageDir.equalsIgnoreCase("")) {
            storageDir = System.getProperty("user.dir");
        }
        KEYSTORE_PATH = storageDir + "/keystore";
    }

    private ApiAionImpl api;
    private AionImpl impl;
    private AionRepositoryImpl repo;

    @Before
    public void setup() {
        CfgAion.inst().getDb().setPath(DATABASE_PATH);
        impl = AionImpl.inst();
        api = new ApiAionImpl(impl);
        repo = AionRepositoryImpl.inst();
        testStartTime = System.currentTimeMillis();
    }

    @After
    public void tearDown() {
        // get a list of all the files in keystore directory
        File folder = new File(KEYSTORE_PATH);

        if (folder == null) return;

        File[] AllFilesInDirectory = folder.listFiles();

        // check for invalid or wrong path - should not happen
        if (AllFilesInDirectory == null) return;

        for (File file : AllFilesInDirectory) {
            if (file.lastModified() >= testStartTime) file.delete();
        }
        folder = new File(DATABASE_PATH);

        if (folder == null) return;

        try {
            FileUtils.deleteRecursive(folder.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreate() {
        System.out.println("API Version = " + api.getApiVersion());
        assertNotNull(api.getInstalledFltrs());
    }

    @Test
    public void testInitNrgOracle() {
        api.initNrgOracle(impl);
        assertNotNull(api.getNrgOracle());
        // Initing a second time should not create a new NrgOracle
        api.initNrgOracle(impl);
        assertNotNull(api.getNrgOracle());
    }

    @Test
    public void testStartES() throws Exception {
        api.startES("thName");
        api.addEvents();
        Thread.sleep(2000);
        api.shutDownES();
        assertTrue(api.allFlagsSet());
    }

    @Test
    public void testGetBlock() {
        assertNotNull(api.getBlockTemplate());

        AionBlock blk = impl.getBlockchain().getBestBlock();

        // sanity check
        assertEquals(blk, api.getBestBlock());

        // getBlock() returns the bestBlock if given -1
        assertEquals(blk, api.getBlock(-1));

        // retrieval based on block hash
        assertTrue(api.getBlockByHash(blk.getHash()).isEqual(blk));

        // retrieval based on block number
        assertTrue(api.getBlock(blk.getNumber()).isEqual(blk));

        // retrieval based on block number that also gives total difficulty
        Map.Entry<AionBlock, BigInteger> rslt = api.getBlockWithTotalDifficulty(blk.getNumber());

        assertTrue(rslt.getKey().isEqual(blk));

        // check because blk might be the genesis block
        assertEquals(
                rslt.getValue(),
                ((AionBlockStore) impl.getBlockchain().getBlockStore())
                        .getTotalDifficultyForHash(blk.getHash()));

        // retrieving genesis block's difficulty
        assertEquals(
                api.getBlockWithTotalDifficulty(0).getValue(),
                CfgAion.inst().getGenesis().getDifficultyBI());
    }

    @Test
    public void testGetSync() {
        SyncInfo sync = api.getSync();
        assertNotNull(sync);
        assertEquals(sync.done, impl.isSyncComplete());
        if (impl.getInitialStartingBlockNumber().isPresent())
            assertEquals(
                    (long) impl.getInitialStartingBlockNumber().get(), sync.chainStartingBlkNumber);
        else assertEquals(0L, sync.chainStartingBlkNumber);
        if (impl.getNetworkBestBlockNumber().isPresent())
            assertEquals((long) impl.getNetworkBestBlockNumber().get(), sync.networkBestBlkNumber);
        else assertEquals(0L, sync.networkBestBlkNumber);
        if (impl.getLocalBestBlockNumber().isPresent())
            assertEquals((long) impl.getLocalBestBlockNumber().get(), sync.chainBestBlkNumber);
        else assertEquals(0L, sync.chainBestBlkNumber);
    }

    @Test
    @Ignore
    public void testGetTransactions() {
        AionBlock parentBlk = impl.getBlockchain().getBestBlock();
        byte[] msg = "test message".getBytes();
        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        AionAddress.ZERO_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewBlock(parentBlk, Collections.singletonList(tx), false);

        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        impl.getAionHub().getBlockchain().add(blk);

        assertTrue(blk.isEqual(api.getBlockByHash(blk.getHash())));
        assertEquals(tx, api.getTransactionByBlockHashAndIndex(blk.getHash(), 0));
        assertEquals(tx, api.getTransactionByBlockNumberAndIndex(blk.getNumber(), 0));
        assertEquals(1, api.getBlockTransactionCountByNumber(blk.getNumber()));
        assertEquals(1, api.getTransactionCountByHash(blk.getHash()));

        blk = api.getBlockByHash(blk.getHash());

        assertEquals(
                1,
                api.getTransactionCount(
                        blk.getTransactionsList().get(0).getFrom(), blk.getNumber()));
        assertEquals(0, api.getTransactionCount(AionAddress.EMPTY_ADDRESS(), blk.getNumber()));

        assertEquals(tx, api.getTransactionByHash(tx.getHash()));
    }

    @Test
    public void testDoCall() {
        byte[] msg = "test message".getBytes();

        AionAddress addr = new AionAddress(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        addr,
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        ArgTxCall txcall =
                new ArgTxCall(
                        addr,
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000);

        assertNotNull(api.doCall(txcall));
        tearDown();
    }

    @Test
    public void testEstimates() {
        byte[] msg = "test message".getBytes();

        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionTransaction tx =
                new AionTransaction(
                        repo.getNonce(AionAddress.ZERO_ADDRESS()).toByteArray(),
                        addr,
                        AionAddress.ZERO_ADDRESS(),
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000);
        tx.sign(new ECKeyEd25519());

        ArgTxCall txcall =
                new ArgTxCall(
                        addr,
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(impl.estimateTxNrg(tx, api.getBestBlock()), api.estimateNrg(txcall));
        tearDown();
    }

    @Test
    public void testCreateContract() {
        byte[] msg = "test message".getBytes();

        // null params returns INVALID_TX

        ArgTxCall txcall = null;

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_TX);

        // null from and empty from return INVALID_FROM

        txcall =
                new ArgTxCall(
                        null,
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_FROM);

        txcall =
                new ArgTxCall(
                        AionAddress.EMPTY_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_FROM);

        // locked account should throw INVALID_ACCOUNT

        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        txcall =
                new ArgTxCall(
                        addr,
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_ACCOUNT);
    }

    @Test
    public void testAccountGetters() {
        assertEquals(
                repo.getBalance(AionAddress.ZERO_ADDRESS()), api.getBalance(AionAddress.ZERO_ADDRESS()));
        assertEquals(repo.getNonce(AionAddress.ZERO_ADDRESS()), api.getNonce(AionAddress.ZERO_ADDRESS()));
        assertEquals(
                repo.getBalance(AionAddress.ZERO_ADDRESS()),
                api.getBalance(AionAddress.ZERO_ADDRESS().toString()));
        assertEquals(
                repo.getNonce(AionAddress.ZERO_ADDRESS()),
                api.getNonce(AionAddress.ZERO_ADDRESS().toString()));
    }

    @Test
    public void testSendTransaction() {

        byte[] msg = "test message".getBytes();

        // null params returns INVALID_TX

        ArgTxCall txcall = null;

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_TX);

        // null from and empty from return INVALID_FROM

        txcall =
                new ArgTxCall(
                        null,
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_FROM);

        txcall =
                new ArgTxCall(
                        AionAddress.EMPTY_ADDRESS(),
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_FROM);

        // locked account should throw INVALID_ACCOUNT

        AionAddress addr = new AionAddress(Keystore.create("testPwd"));

        txcall =
                new ArgTxCall(
                        addr,
                        AionAddress.ZERO_ADDRESS(),
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000);

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_ACCOUNT);
    }

    @Test
    public void testSimpleGetters() {
        assertEquals(
                CfgAion.inst().getApi().getNrg().getNrgPriceDefault(),
                api.getRecommendedNrgPrice());
        api.initNrgOracle(impl);
        assertEquals(api.getNrgOracle().getNrgPrice(), api.getRecommendedNrgPrice());

        assertNotNull(api.getCoinbase());
        assertEquals(repo.getCode(AionAddress.ZERO_ADDRESS()), api.getCode(AionAddress.ZERO_ADDRESS()));
        assertEquals(impl.getBlockMiner().isMining(), api.isMining());
        assertArrayEquals(CfgAion.inst().getNodes(), api.getBootNodes());
        assertEquals(impl.getAionHub().getP2pMgr().getActiveNodes().size(), api.peerCount());
        assertNotNull(api.p2pProtocolVersion());
        assertNotEquals(0, api.getDefaultNrgLimit());
        assertEquals(impl.getAionHub().getP2pMgr().chainId(), Integer.parseInt(api.chainId()));
    }

    @Test
    public void testHashRate() {
        double hashRate = 1000;

        assertTrue(api.setReportedHashrate(Double.toString(hashRate), ""));
        assertEquals(
                impl.getBlockMiner().getHashrate() + hashRate,
                Double.parseDouble(api.getHashrate()),
                0.001);
    }
}
