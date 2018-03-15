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
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author chris
 */
final class TaskGetHeaders implements Runnable {

    private final IP2pMgr p2p;

    private final AtomicReference<NetworkStatus> networkStatus;

    private final AtomicLong jump;

    private final int syncForwardMax;

    TaskGetHeaders(final IP2pMgr _p2p, final AtomicReference<NetworkStatus> _networkStatus, final AtomicLong _jump, int _syncForwardMax){
        this.p2p = _p2p;
        this.networkStatus = _networkStatus;
        this.jump = _jump;
        this.syncForwardMax = _syncForwardMax;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        Set<Integer> ids = new HashSet<>();
        Collection<INode> preFilter = this.p2p.getActiveNodes().values();
        List<INode> filtered = preFilter.stream().filter(
                (n) -> this.networkStatus.get().totalDiff != null &&
                        n.getTotalDifficulty() != null &&
                        (new BigInteger(1, n.getTotalDifficulty())).compareTo(this.networkStatus.get().totalDiff) >= 0).collect(Collectors.toList());
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < 2; i++) {
            if (filtered.size() > 0) {
                INode node = filtered.get(r.nextInt(filtered.size()));
                if (!ids.contains(node.getIdHash())) {
                    ids.add(node.getIdHash());
                    this.p2p.send(node.getIdHash(), new ReqBlocksHeaders(jump.get(), this.syncForwardMax));
                }
            }
        }
    }
}
