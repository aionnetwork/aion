package org.aion.zero.impl.sync;

import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.p2p.Handler;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;

/**
 * Unit tests for block propagation
 */
public class BlockPropagationTest {

    private static class NodeMock implements INode {

        private final byte[] nodeId;
        private final long latestBlockNumber;

        public NodeMock(byte[] nodeId, long latestBlockNumber) {
            this.nodeId = nodeId;
            this.latestBlockNumber = latestBlockNumber;
        }

        @Override
        public byte[] getId() {
            return this.nodeId;
        }

        @Override
        public int getIdHash() {
            return Arrays.hashCode(nodeId);
        }

        @Override
        public long getBestBlockNumber() {
            return this.latestBlockNumber;
        }

        @Override
        public byte[] getIp() {
            return new byte[0];
        }

        @Override
        public String getIdShort() {
            return null;
        }

        @Override
        public String getIpStr() {
            return null;
        }

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        public long getTotalDifficulty() {
            return 0;
        }

        @Override
        public void updateStatus(long _bestBlockNumber, byte[] _bestBlockHash, long _totalDifficulty) {

        }
    }

    private static class P2pMock implements IP2pMgr {

        private Map<Integer, INode> map;

        public P2pMock(final Map<Integer, INode> map) {
            this.map = map;
        }

        @Override
        public Map<Integer, INode> getActiveNodes() {
            return map;
        }

        @Override
        public INode getRandom() {
            return null;
        }

        @Override
        public void shutdown() {

        }

        @Override
        public void run() {

        }

        @Override
        public String version() {
            return null;
        }

        @Override
        public void register(List<Handler> _hs) {

        }

        @Override
        public void send(int _id, Msg _msg) {

        }
    }

    private static List<ECKey> generateDefaultAccounts() {
        List<ECKey> accs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            accs.add(ECKeyFac.inst().create());
        }
        return accs;
    }

    /**
     * Test that we don't propagate back to the sender
     */
    @Test
    public void testBlockPropagationReceiver() {
        List<ECKey> accounts = generateDefaultAccounts();

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts(accounts)
                .build();

        AionBlock block = bundle.bc.createNewBlock(bundle.bc.getGenesis(), Collections.EMPTY_LIST);
        assertThat(block.getNumber()).isEqualTo(1);

        byte[] sender = HashUtil.h256("node1".getBytes());
        NodeMock senderNode = new NodeMock(sender, 1);

        Map<Integer, INode> node = new HashMap<>();
        node.put(1, senderNode);

        P2pMock p2pMock = new P2pMock(node) {
            @Override
            public void send(int nodeId, Msg _msg) {
                throw new RuntimeException("should not have called send");
            }
        };

        StandaloneBlockchain.Bundle anotherBundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts(accounts)
                .build();

        BlockPropagationHandler handler = new BlockPropagationHandler(
                1024,
                anotherBundle.bc, // NOTE: not the same blockchain that generated the block
                p2pMock,
                anotherBundle.bc.getBlockHeaderValidator());

        assertThat(handler.processIncomingBlock(senderNode.getIdHash(), block)).isEqualTo(BlockPropagationHandler.Status.CONNECTED);
    }

    // given two peers, and one sends you a new block, propagate to the other
    @Test
    public void testPropagateBlockToPeer() {
        List<ECKey> accounts = generateDefaultAccounts();

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts(accounts)
                .build();

        AionBlock block = bundle.bc.createNewBlock(bundle.bc.getGenesis(), Collections.EMPTY_LIST);
        assertThat(block.getNumber()).isEqualTo(1);

        byte[] sender = HashUtil.h256("node1".getBytes());
        byte[] receiver = HashUtil.h256("receiver".getBytes());

        NodeMock senderMock = new NodeMock(sender, 1);
        NodeMock receiverMock = new NodeMock(receiver, 0);

        Map<Integer, INode> node = new HashMap<>();
        node.put(1, senderMock);
        node.put(2, receiverMock);

        AtomicInteger times = new AtomicInteger();
        P2pMock p2pMock = new P2pMock(node) {
            @Override
            public void send(int _nodeId, Msg _msg) {
                if (_nodeId != receiverMock.getIdHash())
                    throw new RuntimeException("should only send to receiver");
                times.getAndIncrement();
            }
        };

        StandaloneBlockchain.Bundle anotherBundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts(accounts)
                .build();

        assertThat(bundle.bc.genesis.getHash()).isEqualTo(anotherBundle.bc.genesis.getHash());
        assertThat(block.getParentHash()).isEqualTo(bundle.bc.genesis.getHash());
        assertThat(block.getParentHash()).isEqualTo(anotherBundle.bc.genesis.getHash());

        AionBlock bestBlock = bundle.bc.getBestBlock();
        assertThat(bestBlock.getHash()).isEqualTo(anotherBundle.bc.genesis.getHash());

        BlockPropagationHandler handler = new BlockPropagationHandler(
                1024,
                anotherBundle.bc, // NOTE: not the same blockchain that generated the block
                p2pMock,
                anotherBundle.bc.getBlockHeaderValidator());

        // block is processed
        assertThat(handler.processIncomingBlock(senderMock.getIdHash(), block))
                .isEqualTo(BlockPropagationHandler.Status.CONNECTED);
        assertThat(times.get()).isEqualTo(1);
    }

    @Test
    public void testIgnoreSameBlock() {
        List<ECKey> accounts = generateDefaultAccounts();

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts(accounts)
                .build();

        AionBlock block = bundle.bc.createNewBlock(bundle.bc.getGenesis(), Collections.EMPTY_LIST);
        assertThat(block.getNumber()).isEqualTo(1);

        byte[] sender = HashUtil.h256("node1".getBytes());
        byte[] receiver = HashUtil.h256("receiver".getBytes());

        NodeMock senderMock = new NodeMock(sender, 1);
        NodeMock receiverMock = new NodeMock(receiver, 0);

        Map<Integer, INode> node = new HashMap<>();
        node.put(1, senderMock);
        node.put(2, receiverMock);

        AtomicInteger times = new AtomicInteger();
        P2pMock p2pMock = new P2pMock(node) {
            @Override
            public void send(int _nodeId, Msg _msg) {
                if (_nodeId != receiverMock.getIdHash())
                    throw new RuntimeException("should only send to receiver");
                times.getAndIncrement();
            }
        };

        StandaloneBlockchain.Bundle anotherBundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts(accounts)
                .build();

        assertThat(bundle.bc.genesis.getHash()).isEqualTo(anotherBundle.bc.genesis.getHash());

        BlockPropagationHandler handler = new BlockPropagationHandler(
                1024,
                anotherBundle.bc, // NOTE: not the same blockchain that generated the block
                p2pMock,
                anotherBundle.bc.getBlockHeaderValidator());

        // block is processed
        assertThat(handler.processIncomingBlock(senderMock.getIdHash(), block)).isEqualTo(BlockPropagationHandler.Status.CONNECTED);
        assertThat(handler.processIncomingBlock(senderMock.getIdHash(), block)).isEqualTo(BlockPropagationHandler.Status.DROPPED);
        assertThat(times.get()).isEqualTo(1);
    }
}
