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
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * @author chris
 */
final class TaskGetHeaders implements Runnable {

    private final IP2pMgr p2p;

    private final long selfNumber;

    private final BigInteger selfTd;

    private final int backwardMin;

    private final int backwardMax;

    private final int requestMax;

    private final Logger log;

    private final Random random = new Random(System.currentTimeMillis());

    TaskGetHeaders(IP2pMgr p2p, long selfNumber, BigInteger selfTd, int backwardMin, int backwardMax, int requestMax,
            Logger log) {
        this.p2p = p2p;
        this.selfNumber = selfNumber;
        this.selfTd = selfTd;
        this.backwardMin = backwardMin;
        this.backwardMax = backwardMax;
        this.requestMax = requestMax;
        this.log = log;
    }

    @Override
    public void run() {
        // get all active nodes
        Collection<INode> nodes = this.p2p.getActiveNodes().values();

        // filter nodes by total difficulty
        List<INode> nodesFiltered = nodes.stream()
                .filter((n) -> n.getTotalDifficulty() != null && n.getTotalDifficulty().compareTo(this.selfTd) >= 0)
                .collect(Collectors.toList());
        if (nodesFiltered.isEmpty()) {
            return;
        }

        // sort by TD and pick top 8 in next step.
        nodesFiltered.sort((n1, n2) -> {
            return n2.getTotalDifficulty().compareTo(n1.getTotalDifficulty());
        });

        // @TODO: when nodes TD highly distributed in wide range, simple way is only pick top 8 node for sync.
        // looking for better strategy here.
        INode node = nodesFiltered.get(random.nextInt(Math.min(nodesFiltered.size(), 8)));

        if (log.isDebugEnabled()) {
            log.debug("<sync with={} BB={}>", node.getIdShort(), node.getBestBlockNumber());
        }

        long nodeNumber = node.getBestBlockNumber();
        long from = 0;
        if (nodeNumber >= selfNumber + 128) {
            from = Math.max(1, selfNumber - backwardMin);
        } else if (nodeNumber >= selfNumber - 128) {
            from = Math.max(1, selfNumber - backwardMax);
        } else {
            // no need to request from this node. His TD is probably corrupted.
            return;
        }

        // send request
        if (log.isDebugEnabled()) {
            log.debug("<get-headers from-num={} size={} node={}>", from, requestMax, node.getIdShort());
        }
        ReqBlocksHeaders rbh = new ReqBlocksHeaders(from, requestMax);
        this.p2p.send(node.getIdHash(), rbh);
    }
}
