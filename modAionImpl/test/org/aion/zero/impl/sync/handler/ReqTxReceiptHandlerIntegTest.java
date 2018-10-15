package org.aion.zero.impl.sync.handler;

import org.aion.api.type.BlockDetails;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.db.AbstractRepository;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.vm.types.DataWord;
import org.aion.p2p.Handler;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ReqTxReceipts;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReqTxReceiptHandlerIntegTest {

    @Test
    public void test() throws Exception {
        P2pMgr p2p = new P2pMgr(
                128
                , Version.KERNEL_VERSION
                , "55de5555-5e55-55b5-b555-555d55fa555c"
                , "127.0.0.1"
                , 30303
                , new String[] { "p2p://33de4394-9e65-40b1-b241-701d82fa782c@127.0.0.1:35303" }
//                , new String[] { "p2p://99de9999-9e99-99b9-b999-999d99fa999c@127.0.0.1:35303" }
                , false
                , 10
                , 10
                , false
                ,50
        );

        Logger syncLOG = mock(Logger.class);
        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        when(blockchain.getBestBlock()).thenReturn(createDummyBlock());
        SyncMgr syncMgr = mock(SyncMgr.class);
        byte[] genesis = ByteUtils.fromHexString("0de78f41308a5cb451d9a89c298335dec96961963378226e3c788059aade7d46");
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
        cbs.add(new ResTxReceiptHandler(transactionStore, blockStore));
        cbs.add(new ReqTxReceiptHandler(p2p, blockchain));
        p2p.register(cbs);

        p2p.run();

        System.out.println("sleep #1");
        Thread.sleep(10000);

        System.out.println("sending");
        byte[] tx = ByteUtils.fromHexString("7e21ba25b690afcb4e76adbb44b3147b30cd20969dffb9c252992fdcdaef9bc7");
        p2p.send(717142562, "33de43", new ReqTxReceipts(tx));

        System.out.println("sleep #2");
        Thread.sleep(10000);

        ArgumentCaptor<AbstractTxInfo> infoCapture = ArgumentCaptor.forClass(AbstractTxInfo.class);
        verify(transactionStore).putToBatch(infoCapture.capture());

        List<AbstractTxInfo> capturedTxs = infoCapture.getAllValues();

        // currently hardcoded to a particular blockchain state ... need to genericize for a proper integ test
        assertThat(capturedTxs.size(), is(1));
        assertThat(ByteUtil.toHexString(capturedTxs.get(0).getBlockHash()), is("3d62a9bd04d960329264248576d89cb00da7648819d2502a483a4002fc962993"));
        assertThat(capturedTxs.get(0).getIndex(), is(0));
        assertThat( ((AionTxReceipt)capturedTxs.get(0).getReceipt()).getEnergyUsed() , is(21004l));
    }

    @Test
    public void crashTest() throws Exception {
        P2pMgr p2p = new P2pMgr(
                128
                , Version.KERNEL_VERSION
                , "55de5555-5e55-55b5-b555-555d55fa555c"
                , "127.0.0.1"
                , 30303
                , new String[] { "p2p://33de4394-9e65-40b1-b241-701d82fa782c@127.0.0.1:35303" }
                , false
                , 10
                , 10
                , false
                ,50
        );

        p2p.register(Collections.emptyList());
        p2p.run();
        Thread.sleep(10000);
    }

    public static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(Address.ADDRESS_LEN);
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

        // TODO: set a dummy limit of 5000000 for now
        return new AionBlock(parentHash, Address.wrap(coinbase), logsBloom, difficulty, number, timestamp, extraData, nonce,
                receiptsRoot, transactionsRoot, stateRoot, transactionsList, solutions, 0, 5000000);
    }
}