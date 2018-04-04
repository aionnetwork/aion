package org.aion.zero.impl.sync.state;


import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;

public class SyncPeerStateTest {

    private static final String DEFAULT_ID = "HelloWorld";
    private static final int DEFAULT_HASH = (new Random()).nextInt();

    @Test
    public void testDefaultState() {
        final SyncPeerState peerState = new SyncPeerState(DEFAULT_ID, DEFAULT_HASH);

        assertThat(peerState.getShortId()).isEqualTo(DEFAULT_ID);
        assertThat(peerState.getIdHashCode()).isEqualTo(DEFAULT_HASH);
        assertThat(peerState.canSendBodies()).isTrue();
        assertThat(peerState.canSendHeaders()).isTrue();
        assertThat(peerState.getTotalDifficulty()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testParseInputMessage() {
        final long blockNumber = 1000L;
        final BigInteger blockTD = BigInteger.valueOf(10000L);
        final SyncPeerState peerState = new SyncPeerState(DEFAULT_ID, DEFAULT_HASH);
        final long currentTimestamp = System.currentTimeMillis();

        peerState.processStatusUpdateInternal(blockNumber, blockTD, currentTimestamp);
        assertThat(peerState.getLastReceivedMessageTimestamp()).isEqualTo(currentTimestamp);
    }

    @Test
    public void testBlockHeaderLoop() {
        final long startingBlockNumber = 42L;
        final SyncPeerState peerState = new SyncPeerState(DEFAULT_ID, DEFAULT_HASH);

        // lets assume we receive headers before we sent a request, should be invalid
        assertThat(peerState.checkReceiveHeadersValid(startingBlockNumber)).isFalse();

        peerState.updateHeadersSent(42L);

        // pretend blocks came back
        assertThat(peerState.checkReceiveHeadersValid(41L)).isFalse();
        assertThat(peerState.checkReceiveHeadersValid(42L)).isTrue();
    }

    @Test
    public void testBlockBodyLoop() {
        final long startingBlockNumber = 42L;
        final SyncPeerState peerState = new SyncPeerState(DEFAULT_ID, DEFAULT_HASH);

        // lets assume we receive headers before we sent a request, should be invalid
        assertThat(peerState.checkReceiveBodiesValid(startingBlockNumber)).isFalse();

        peerState.updateBodiesSent(42L);

        // pretend blocks came back
        assertThat(peerState.checkReceiveBodiesValid(41L)).isFalse();
        assertThat(peerState.checkReceiveBodiesValid(42L)).isTrue();
    }

}
