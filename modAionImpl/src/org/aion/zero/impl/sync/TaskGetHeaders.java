/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 */

package org.aion.zero.impl.sync;

import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.sync.state.SyncPeerSet;
import org.aion.zero.impl.sync.state.SyncPeerState;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author chris
 */
final class TaskGetHeaders implements Runnable {

    private static final int NUM_SEND_REQ = 2;

    private final IP2pMgr p2p;

    private long fromBlock;

    private final int syncMax;

    private final BigInteger selfTd;

    private final Logger log;

    private final SyncPeerSet peerSet;

    private final Random random = new Random();

    TaskGetHeaders(final IP2pMgr _p2p, long _fromBlock, int _syncMax, BigInteger _selfTd, Logger log, SyncPeerSet peerSet){
        this.p2p = _p2p;
        this.fromBlock = _fromBlock;
        this.syncMax = _syncMax;
        this.selfTd = _selfTd;
        this.log = log;
        this.peerSet = peerSet;
    }

    @Override
    public void run() {
        List<SyncPeerState> sendNodes = new ArrayList<>();
        synchronized (this.peerSet) {
            List<SyncPeerState> allPeers = this.peerSet.getAbleToSendHeaderPeers(this.selfTd);

            for (int i = 0; i < NUM_SEND_REQ; i++) {
                if (allPeers.isEmpty())
                    break;
                SyncPeerState chosenPeer = allPeers.get(this.random.nextInt(allPeers.size()));
                allPeers.remove(chosenPeer);
                sendNodes.add(chosenPeer);
            }

            for (SyncPeerState peer : sendNodes) {
                peer.updateHeadersSent(this.fromBlock);
            }
        }

        if (!sendNodes.isEmpty()) {
            for (SyncPeerState peer : sendNodes) {
                ReqBlocksHeaders rbh = new ReqBlocksHeaders(this.fromBlock, this.syncMax);

                if (log.isDebugEnabled()) {
                    log.debug("<get-headers from-num={} size={} node={}>",
                            fromBlock, syncMax, peer.getShortId());
                }
                this.p2p.send(peer.getIdHashCode(), rbh);
            }
        }
        // end
    }
}
