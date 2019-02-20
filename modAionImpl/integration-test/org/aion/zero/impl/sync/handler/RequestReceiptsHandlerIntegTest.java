package org.aion.zero.impl.sync.handler;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.vm.types.DataWord;
import org.aion.p2p.Handler;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.RequestReceipts;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

public class RequestReceiptsHandlerIntegTest {

    /**
     * Transaction hash that we'll request receipt for. This particular tx is the first tx on the
     * mastery network at block 213091:
     *
     * <p>Information about this block from Web3: <tt>{ blockHash:
     * '0xafdaf9af7033a6b08c891e2df04469cd3feb571b010134d2556fcd2bd5de1b7e', nrg: 22000,
     * transactionIndex: 0, nonce: 1, input: '0x00', blockNumber: 213091, gas: 22000, from:
     * '0xA0112D226E668107793692a54e2524850431a1a2F3005626c588deBf75EE48Fa', to:
     * '0xA045546D448E2130F7eC9F1E8F5452d3a133DA2484F28C4f108B1a4DF2ae4e4A', value:
     * '7000000000000000000', hash:
     * '0xfd9d619166e1e0c8f60c6fd6bacb8e2629d5429db9d435034c79b1cd1d41c700', gasPrice: '10000000000'
     * }</tt>
     *
     * <p></tt>
     */
    private byte[] TEST_TX =
            ByteUtils.fromHexString(
                    "fd9d619166e1e0c8f60c6fd6bacb8e2629d5429db9d435034c79b1cd1d41c700");

    /**
     * This test requires a running kernel to work, as it will send it a receipt request and verify
     * the response. The kernel needs to be set up in the following way in order for the test to
     * work (or you can modify the test to match your kernel): - must be on mastery (have net id 32)
     * and synced up to at least block 213091 - must run on local host on port 35303 - use id
     * 33de4394-9e65-40b1-b241-701d82fa782c
     */
    @Test
    public void test() throws Exception {
        // start up a P2pMgr, hardcoded to connect to one kernel that we need to manaully start up
        // ideally, the test should start up that kernel with the appropriate config
        P2pMgr p2p =
                new P2pMgr(
                        32,
                        Version.KERNEL_VERSION,
                        "55de5555-5e55-55b5-b555-555d55fa555c",
                        "0.0.0.0",
                        30303,
                        new String[] {"p2p://33de4394-9e65-40b1-b241-701d82fa782c@127.0.0.1:35303"},
                        false,
                        10,
                        10,
                        true,
                        50);

        Logger syncLOG = mock(Logger.class);
        when(syncLOG.isTraceEnabled()).thenReturn(true);
        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        when(blockchain.getBestBlock()).thenReturn(createDummyBlock());
        SyncMgr syncMgr = mock(SyncMgr.class);
        byte[] genesis =
                ByteUtils.fromHexString(
                        "0de78f41308a5cb451d9a89c298335dec96961963378226e3c788059aade7d46");
        TransactionStore transactionStore = mock(TransactionStore.class);

        AionBlockStore blockStore = mock(AionBlockStore.class);
        AionBlock block = mock(AionBlock.class);
        List<AionTransaction> transactionsList = new LinkedList<>();
        when(blockStore.getBlockByHash(Mockito.any((byte[].class)))).thenReturn(block);
        when(block.getTransactionsList()).thenReturn(transactionsList);
        transactionsList.add(mock(AionTransaction.class));

        List<Handler> cbs = new ArrayList<>();
        cbs.add(new ReqStatusHandler(syncLOG, blockchain, p2p, genesis));
        cbs.add(new ResStatusHandler(syncLOG, p2p, syncMgr));
        cbs.add(new ResponseReceiptsHandler(transactionStore, blockStore));
        cbs.add(new RequestReceiptsHandler(p2p, blockchain));
        p2p.register(cbs);

        p2p.run();

        // not ideal to randomly sleep and hope that we've slept long enough, but
        // there's no easy way to hook into these components to observe their state
        // right now
        System.out.println("Sleeping to let P2pMgr start up");
        Thread.sleep(20000);

        System.out.println("Sending tx receipts request");
        p2p.send(717142562, "33de43", new RequestReceipts(TEST_TX));

        System.out.println("Sleeping to wait for response to our tx receipts request");
        Thread.sleep(20000);

        ArgumentCaptor<AbstractTxInfo> infoCapture = ArgumentCaptor.forClass(AbstractTxInfo.class);
        verify(transactionStore).putToBatch(infoCapture.capture());

        List<AbstractTxInfo> capturedTxs = infoCapture.getAllValues();

        // currently hardcoded to a particular blockchain state ... need to genericize for a proper
        // integ test
        assertThat(capturedTxs.size(), is(1));
        assertThat(
                ByteUtil.toHexString(capturedTxs.get(0).getBlockHash()),
                is("afdaf9af7033a6b08c891e2df04469cd3feb571b010134d2556fcd2bd5de1b7e"));
        assertThat(capturedTxs.get(0).getIndex(), is(0));
        assertThat(((AionTxReceipt) capturedTxs.get(0).getReceipt()).getEnergyUsed(), is(21004l));
    }

    @Test
    public void crashTest() throws Exception {
        P2pMgr p2p =
                new P2pMgr(
                        128,
                        Version.KERNEL_VERSION,
                        "55de5555-5e55-55b5-b555-555d55fa555c",
                        "127.0.0.1",
                        30303,
                        new String[] {"p2p://33de4394-9e65-40b1-b241-701d82fa782c@127.0.0.1:35303"},
                        false,
                        10,
                        10,
                        false,
                        50);

        p2p.register(Collections.emptyList());
        p2p.run();
        Thread.sleep(10000);
    }

    public static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(AionAddress.SIZE);
        byte[] logsBloom = new byte[0];
        byte[] difficulty = new DataWord(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[0];

        return new AionBlock(
                parentHash,
                AionAddress.wrap(coinbase),
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
}
