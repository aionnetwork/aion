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

import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.SyncInfo;
import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

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
    }

    @Test
    public void TestInitNrgOracle() {
        System.out.println("run TestInitNrgOracle.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);
        api.initNrgOracle(impl);
        assertNotNull(api.getNrgOracle());
        // Initing a second time should not create a new NrgOracle
        api.initNrgOracle(impl);
        assertNotNull(api.getNrgOracle());
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

        assertEquals(blk, api.getBlock(-1));

        assertEquals(blk.toString(), api.getBlockByHash(blk.getHash()).toString());
        assertEquals(blk.toString(), api.getBlock(blk.getNumber()).toString());

        Map.Entry rslt = api.getBlockWithTotalDifficulty(blk.getNumber());
        assertEquals(rslt.getKey().toString(), blk.toString());
        if (!blk.isGenesis())
            assertEquals(rslt.getValue(),
                    ((AionBlockStore)impl.getBlockchain().getBlockStore()).getTotalDifficultyForHash(blk.getHash()));
        assertEquals(api.getBlockWithTotalDifficulty(0).getValue(), CfgAion.inst().getGenesis().getDifficultyBI());
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

    @Test
    public void TestGetTransactions() {
        System.out.println("run TestGetTransactions.");
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        AionBlock parentBlk = impl.getBlockchain().getBestBlock();
        byte[] msg = "test message".getBytes();
        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                    msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        AionBlock blk = impl.getAionHub().getBlockchain().createNewBlock(parentBlk,
                Collections.singletonList(tx), false);

        assertNotNull(blk);
        assertNotEquals(blk.getTransactionsList().size(), 0);

        impl.getAionHub().getBlockchain().add(blk);

        assertTrue(blk.isEqual(api.getBlockByHash(blk.getHash())));
        assertEquals(tx, api.getTransactionByBlockHashAndIndex(blk.getHash(), 0));
        assertEquals(tx, api.getTransactionByBlockNumberAndIndex(blk.getNumber(), 0));
        assertEquals(1, api.getBlockTransactionCountByNumber(blk.getNumber()));
        assertEquals(1, api.getTransactionCountByHash(blk.getHash()));

        blk = api.getBlockByHash(blk.getHash());

        assertEquals(1, api.getTransactionCount(
                blk.getTransactionsList().get(0).getFrom(), blk.getNumber()));
        assertEquals(0, api.getTransactionCount(Address.EMPTY_ADDRESS(), blk.getNumber()));

        assertEquals(tx, api.getTransactionByHash(tx.getHash()));

    }

    @Test
    public void TestDoCall() {
        System.out.println("run TestDoCall.");
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        byte[] msg = "test message".getBytes();

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                addr, Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());


        ArgTxCall txcall = new ArgTxCall(addr, Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNotNull(api.doCall(txcall));
    }

    @Test
    public void TestEstimates() {
        System.out.println("run TestEstimates.");
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        byte[] msg = "test message".getBytes();

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                addr, Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());


        ArgTxCall txcall = new ArgTxCall(addr, Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNotEquals(0, api.estimateGas(txcall));
        assertEquals(impl.estimateTxNrg(tx, api.getBestBlock()), api.estimateNrg(txcall));
    }

    @Test
    public void TestCreateContract() {
        System.out.println("run TestCreateContract.");
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        byte[] msg = "test message".getBytes();

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        ArgTxCall txcall = new ArgTxCall(addr, Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNotNull(api.createContract(txcall).transId);
        assertNotNull(api.createContract(txcall).address);

        txcall = new ArgTxCall(null, Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNull(api.createContract(txcall));

        txcall = new ArgTxCall(Address.ZERO_ADDRESS(), Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNull(api.createContract(txcall));
    }

    @Test
    public void TestAccountGetters() {
        System.out.println("run TestTransactionGetters.");
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        assertEquals(repo.getBalance(Address.ZERO_ADDRESS()), api.getBalance(Address.ZERO_ADDRESS()));
        assertEquals(repo.getNonce(Address.ZERO_ADDRESS()), api.getNonce(Address.ZERO_ADDRESS()));
        assertEquals(repo.getBalance(Address.ZERO_ADDRESS()), api.getBalance(Address.ZERO_ADDRESS().toString()));
        assertEquals(repo.getNonce(Address.ZERO_ADDRESS()), api.getNonce(Address.ZERO_ADDRESS().toString()));
    }

    @Test
    public void TestSendTransaction() {
        System.out.println("run TestSendTransaction.");
        AionImpl impl = AionImpl.inst();
        AionRepositoryImpl repo = AionRepositoryImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        byte[] msg = "test message".getBytes();

        Address addr = new Address(Keystore.create("testPwd"));
        AccountManager.inst().unlockAccount(addr, "testPwd", 50000);

        ArgTxCall txcall = new ArgTxCall(addr, Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNotNull(api.sendTransaction(txcall));

        AionTransaction tx = new AionTransaction(repo.getNonce(Address.ZERO_ADDRESS()).toByteArray(),
                addr, Address.ZERO_ADDRESS(), BigInteger.ONE.toByteArray(),
                msg,100000, 100000);
        tx.sign(new ECKeyEd25519());

        assertNotNull(api.sendTransaction(tx.getEncoded()));

        txcall = new ArgTxCall(null, Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNull(api.sendTransaction(txcall));

        txcall = new ArgTxCall(Address.EMPTY_ADDRESS(), Address.ZERO_ADDRESS(),
                msg, repo.getNonce(addr), BigInteger.ONE,100000, 100000);

        assertNull(api.sendTransaction(txcall));
    }

    @Test
    public void TestSimpleGetters() {
        System.out.println("run TestSimpleGetters.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        assertEquals(CfgAion.inst().getApi().getNrg().getNrgPriceDefault(),
            api.getRecommendedNrgPrice());
        api.initNrgOracle(impl);
        assertEquals(api.getNrgOracle().getNrgPrice(),
                api.getRecommendedNrgPrice());

        assertNotNull(api.getCoinbase());
        assertEquals(impl.getAionHub().getRepository().getCode(Address.ZERO_ADDRESS()),
                api.getCode(Address.ZERO_ADDRESS()));
        assertEquals(impl.getBlockMiner().isMining(), api.isMining());
        assertArrayEquals(CfgAion.inst().getNodes(), api.getBootNodes());
        assertEquals(impl.getAionHub().getP2pMgr().getActiveNodes().size(), api.peerCount());
        assertNotNull(api.p2pProtocolVersion());
        assertNotEquals(0, api.getDefaultNrgLimit());
        assertEquals(impl.getAionHub().getP2pMgr().chainId(), Integer.parseInt(api.chainId()));
    }

    @Test
    public void TestHashRate() {
        System.out.println("run TestSendTransaction.");
        AionImpl impl = AionImpl.inst();
        ApiAionTests.ApiAionImpl api = new ApiAionTests.ApiAionImpl(impl);

        double hashRate = 1000;

        assertTrue(api.setReportedHashrate(Double.toString(hashRate), ""));
        assertEquals(impl.getBlockMiner().getHashrate() + hashRate, Double.parseDouble(api.getHashrate()), 0.001);
    }
}
