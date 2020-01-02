package org.aion.api.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.undertow.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.SyncInfo;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.api.server.account.AccountManager;
import org.aion.zero.impl.keystore.Keystore;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.types.TxResponse;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.base.AionTxReceipt;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ApiAionTest {
    private ECKey key = ECKeyFac.inst().create();

    private class ApiAionImpl extends ApiAion {

        private boolean onBlockFlag;
        private boolean pendingRcvdFlag;
        private boolean pendingUpdateFlag;

        @Override
        protected void onBlock(AionBlockSummary cbs) {
            onBlockFlag = true;
        }

        @Override
        protected void pendingTxReceived(AionTransaction _tx) {
            pendingRcvdFlag = true;
        }

        @Override
        protected void pendingTxUpdate(AionTxReceipt _txRcpt, int _state) {
            pendingUpdateFlag = true;
        }

        private boolean allFlagsSet() {
            return (onBlockFlag && pendingRcvdFlag && pendingUpdateFlag);
        }

        private ApiAionImpl(AionImpl impl) {
            super(impl, new AccountManager(null));
            onBlockFlag = false;
            pendingRcvdFlag = false;
            pendingUpdateFlag = false;
        }

        private void addEvents() {
            EventBlock evBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);
            AionBlockSummary abs = new AionBlockSummary(null, null, null, null);
            evBlock.setFuncArgs(Collections.singletonList(abs));

            ees.add(evBlock);

            // provokes exception in EpApi.run()
            ees.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
            ees.add(new EventDummy());
        }
    }

    private static  String KEYSTORE_PATH;
    private static final String DATABASE_PATH = "ApiServerTestPath";
    private long testStartTime;
    private ApiAionImpl api;
    private AionImpl impl;
    private AionRepositoryImpl repo;

    @Before
    public void setup() {
        CfgAion.inst().getDb().setPath(DATABASE_PATH);

        impl = AionImpl.instForTest();
        api = new ApiAionImpl(impl);
        repo = AionRepositoryImpl.inst();
        testStartTime = System.currentTimeMillis();
        KEYSTORE_PATH = Keystore.getKeystorePath();

        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();

        // get a list of all the files in keystore directory
        File folder = new File(KEYSTORE_PATH);

        File[] AllFilesInDirectory = folder.listFiles();

        // check for invalid or wrong path - should not happen
        if (AllFilesInDirectory == null) return;

        for (File file : AllFilesInDirectory) {
            if (file.lastModified() >= testStartTime) file.delete();
        }
        folder = new File(DATABASE_PATH);

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
    public void testStartES() throws Exception {
        api.startES("thName");
        api.addEvents();
        Thread.sleep(2000);
        api.shutDownES();
        assertTrue(api.onBlockFlag);
    }

    @Test
    public void testGetBlock() {
        assertNotNull(api.getBlockTemplate());

        Block blk = impl.getBlockchain().getBestBlock();

        // sanity check
        assertEquals(blk, api.getBestBlock());

        // getBlock() returns the bestBlock if given -1
        assertEquals(blk, api.getBlock(-1));

        // retrieval based on block hash
        assertTrue(api.getBlockByHash(blk.getHash()).isEqual(blk));

        // retrieval based on block number
        assertTrue(api.getBlock(blk.getNumber()).isEqual(blk));

        // retrieval based on block number that also gives total difficulty
        Map.Entry<Block, BigInteger> rslt = api.getBlockWithTotalDifficulty(blk.getNumber());

        assertNotNull(rslt);
        assertTrue(rslt.getKey().isEqual(blk));

        // check because blk might be the genesis block
        assertEquals(
                rslt.getValue(),
                impl.getBlockchain().getBlockStore()
                        .getTotalDifficultyForHash(blk.getHash()));

        // retrieving genesis block's difficulty
        assertEquals(
                api.getBlockWithTotalDifficulty(0).getValue(),
                CfgAion.inst().getGenesis().getDifficultyBI());
    }

    /**
     * Tests that getSyncInfo returns the correct information when the local best block number is
     * greater than {@link ApiAion#SYNC_TOLERANCE} blocks below the network best block.
     *
     * <p>{@link ApiAion#SYNC_TOLERANCE} is the number of blocks that the local must be within
     * (compared to the network best) in order for syncing to be considered complete.
     */
    @Test
    public void testGetSyncInfoWhenLocalIsOutsideSyncToleranceAmount() {
        long localBestBlockNumber = 0;
        long networkBestBlockNumber = localBestBlockNumber + ApiAion.SYNC_TOLERANCE + 1;

        SyncInfo syncInfo = api.getSyncInfo(localBestBlockNumber, networkBestBlockNumber);
        assertFalse(syncInfo.done);
        assertEquals(localBestBlockNumber, syncInfo.chainBestBlkNumber);
        assertEquals(networkBestBlockNumber, syncInfo.networkBestBlkNumber);
    }

    /**
     * Tests that getSyncInfo returns the correct information when the local best block number is
     * {@link ApiAion#SYNC_TOLERANCE} blocks below the network best block.
     *
     * <p>{@link ApiAion#SYNC_TOLERANCE} is the number of blocks that the local must be within
     * (compared to the network best) in order for syncing to be considered complete.
     */
    @Test
    public void testGetSyncInfoWhenLocalIsAtSyncToleranceAmount() {
        long localBestBlockNumber = 0;
        long networkBestBlockNumber = localBestBlockNumber + ApiAion.SYNC_TOLERANCE;

        SyncInfo syncInfo = api.getSyncInfo(localBestBlockNumber, networkBestBlockNumber);
        assertTrue(syncInfo.done);
        assertEquals(localBestBlockNumber, syncInfo.chainBestBlkNumber);
        assertEquals(networkBestBlockNumber, syncInfo.networkBestBlkNumber);
    }

    /**
     * Tests that getSyncInfo returns the correct information when the local best block number is
     * less than {@link ApiAion#SYNC_TOLERANCE} blocks below the network best block.
     *
     * <p>{@link ApiAion#SYNC_TOLERANCE} is the number of blocks that the local must be within
     * (compared to the network best) in order for syncing to be considered complete.
     */
    @Test
    public void testGetSyncInfoWhenLocalIsWithinSyncToleranceAmount() {
        long localBestBlockNumber = 0;
        long networkBestBlockNumber = localBestBlockNumber + ApiAion.SYNC_TOLERANCE - 1;

        SyncInfo syncInfo = api.getSyncInfo(localBestBlockNumber, networkBestBlockNumber);
        assertTrue(syncInfo.done);
        assertEquals(localBestBlockNumber, syncInfo.chainBestBlkNumber);
        assertEquals(networkBestBlockNumber, syncInfo.networkBestBlkNumber);
    }

    @Test
    @Ignore
    public void testGetTransactions() {
        Block parentBlk = impl.getBlockchain().getBestBlock();
        byte[] msg = "test message".getBytes();
        AionTransaction tx =
                AionTransaction.create(
                        key,
                        repo.getNonce(AddressUtils.ZERO_ADDRESS).toByteArray(),
                        AddressUtils.ZERO_ADDRESS,
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000,
                        TransactionTypes.DEFAULT, null);

        Block blk =
                impl.getAionHub()
                        .getBlockchain()
                        .createNewMiningBlock(parentBlk, Collections.singletonList(tx), false);

        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        impl.getAionHub().getBlockchain().tryToConnect(blk);

        assertTrue(blk.isEqual(api.getBlockByHash(blk.getHash())));
        assertEquals(tx, api.getTransactionByBlockHashAndIndex(blk.getHash(), 0).tx);
        assertEquals(tx, api.getTransactionByBlockNumberAndIndex(blk.getNumber(), 0).tx);
        assertEquals(1, api.getBlockTransactionCountByNumber(blk.getNumber()));
        assertEquals(1, api.getTransactionCountByHash(blk.getHash()));

        blk = api.getBlockByHash(blk.getHash());

        assertEquals(
                1,
                api.getTransactionCount(
                        blk.getTransactionsList().get(0).getSenderAddress(), blk.getNumber()));
        assertEquals(0, api.getTransactionCount(null, blk.getNumber()));

        assertEquals(tx, api.getTransactionByHash(tx.getTransactionHash()).tx);
    }

    @Test
    public void testDoCall() {
        byte[] msg = "test message".getBytes();

        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));
        api.unlockAccount(addr, "testPwd", 50000);

        ArgTxCall txcall =
                new ArgTxCall(
                        addr,
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertNotNull(api.doCall(txcall));
    }

    @Test
    public void testEstimates() {
        byte[] msg = "test message".getBytes();

        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));
        api.unlockAccount(addr, "testPwd", 50000);

        AionTransaction tx =
                AionTransaction.create(
                        key,
                        repo.getNonce(AddressUtils.ZERO_ADDRESS).toByteArray(),
                        AddressUtils.ZERO_ADDRESS,
                        BigInteger.ONE.toByteArray(),
                        msg,
                        100000,
                        100000,
                        TransactionTypes.DEFAULT,
                        null);

        ArgTxCall txcall =
                new ArgTxCall(
                        addr,
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(impl.estimateTxNrg(tx, api.getBestBlock()), api.estimateNrg(txcall));
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
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_FROM);

        txcall =
                new ArgTxCall(
                        null,
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_FROM);

        // locked account should throw INVALID_ACCOUNT

        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

        txcall =
                new ArgTxCall(
                        addr,
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(api.createContract(txcall).getType(), TxResponse.INVALID_ACCOUNT);
    }

    @Test
    public void testAccountGetters() {
        assertEquals(
                repo.getBalance(AddressUtils.ZERO_ADDRESS),
                api.getBalance(AddressUtils.ZERO_ADDRESS));
        assertEquals(
                repo.getNonce(AddressUtils.ZERO_ADDRESS), api.getNonce(AddressUtils.ZERO_ADDRESS));
        assertEquals(
                repo.getBalance(AddressUtils.ZERO_ADDRESS),
                api.getBalance(AddressUtils.ZERO_ADDRESS.toString()));
        assertEquals(
                repo.getNonce(AddressUtils.ZERO_ADDRESS),
                api.getNonce(AddressUtils.ZERO_ADDRESS.toString()));
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
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_FROM);

        txcall =
                new ArgTxCall(
                        null,
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        BigInteger.ONE,
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_FROM);

        // locked account should throw INVALID_ACCOUNT

        AionAddress addr = AddressUtils.wrapAddress(Keystore.create("testPwd"));

        txcall =
                new ArgTxCall(
                        addr,
                        AddressUtils.ZERO_ADDRESS,
                        msg,
                        repo.getNonce(addr),
                        BigInteger.ONE,
                        100000,
                        100000,
                        null);

        assertEquals(api.sendTransaction(txcall).getType(), TxResponse.INVALID_ACCOUNT);
    }

    @Test
    public void testSimpleGetters() {
        assertEquals(
                CfgAion.inst().getApi().getNrg().getNrgPriceDefault(),
                api.getRecommendedNrgPrice());
        api.initNrgOracle(impl);

        assertNotNull(api.getCoinbase());
        assertEquals(
                repo.getCode(AddressUtils.ZERO_ADDRESS), api.getCode(AddressUtils.ZERO_ADDRESS));
        assertEquals(impl.getBlockMiner().isMining(), api.isMining());
        assertArrayEquals(CfgAion.inst().getNodes(), api.getBootNodes());
        assertEquals(impl.getAionHub().getP2pMgr().getActiveNodes().size(), api.peerCount());
        assertNotNull(api.p2pProtocolVersion());
        assertNotEquals(0, api.getRecommendedNrgPrice());
        assertEquals(impl.getAionHub().getChainId(), Integer.parseInt(api.chainId()));
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
