package org.aion.api.server;

import org.aion.api.server.types.SyncInfo;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ApiAionTests {

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

        public boolean allFlagsSet() {
            return (onBlockFlag && pendingRcvdFlag && pendingUpdateFlag);
        }


        public ApiAionImpl(AionImpl impl) {
            super(impl);
            onBlockFlag = false;
            pendingRcvdFlag = false;
            pendingUpdateFlag = false;
        }

        public void addEvents() {
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

            //provokes exception in EpApi.run()
            ees.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
            ees.add(new EventDummy());
        }

    }

    @Test
    public void TestCreate() {
        System.out.println("run TestCreate.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);
        System.out.println("API Version = " + api.getApiVersion());
        assertNotNull(api.getInstalledFltrs());
        assertNotNull(api.getCoinbase());
    }

    @Test
    public void TestInitNrgOracle() {
        System.out.println("run TestInitNrgOracle.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);
        api.initNrgOracle(impl);
        assertNotNull(ApiAion.NRG_ORACLE);
        // Initing a second time should not create a new NrgOracle
        api.initNrgOracle(impl);
        assertNotNull(ApiAion.NRG_ORACLE);
    }

    @Test
    public void TestStartES() {
        System.out.println("run TestStartES.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);
        api.startES("thName");
        api.addEvents();
        try
        {
            Thread.sleep(5000);
        }
        catch(InterruptedException ex) {}
        api.shutDownES();
        assertTrue(api.allFlagsSet());
    }

    @Test
    public void TestGetBlock() {
        System.out.println("run TestGetBlock.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);
        assertNotNull(api.getBlockTemplate());

        AionBlock blk = impl.getBlockchain().getBestBlock();

        assertEquals(blk, api.getBestBlock());
        assertEquals(blk.toString(), api.getBlockByHash(blk.getHash()).toString());
        assertEquals(blk.toString(), api.getBlock(blk.getNumber()).toString());

        Map.Entry rslt = api.getBlockWithTotalDifficulty(blk.getNumber());
        assertEquals(rslt.getKey().toString(), blk.toString());
        assertEquals(rslt.getValue(), blk.getDifficultyBI());
    }


    @Test
    public void TestGetSync() {
        System.out.println("run TestGetSync.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);
        SyncInfo sync = api.getSync();
        assertNotNull(sync);
        assertEquals(sync.done, impl.isSyncComplete());
        if (impl.getInitialStartingBlockNumber().isPresent())
            assertEquals(sync.chainStartingBlkNumber, (long) impl.getInitialStartingBlockNumber().get());
        if (impl.getInitialStartingBlockNumber().isPresent())
            assertEquals(sync.networkBestBlkNumber, (long) impl.getNetworkBestBlockNumber().get());
        if (impl.getInitialStartingBlockNumber().isPresent())
            assertEquals(sync.chainBestBlkNumber, (long) impl.getLocalBestBlockNumber().get());
    }
}
