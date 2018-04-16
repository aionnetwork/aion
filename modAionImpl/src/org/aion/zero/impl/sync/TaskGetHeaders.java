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
import java.util.*;
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

        Set<Integer> ids = new HashSet<>();
        List<INode> preFilter = this.p2p.getActiveNodes();

        List<INode> filtered = preFilter.stream().filter(
                (n) -> n.getTotalDifficulty() != null &&
                        n.getTotalDifficulty().compareTo(this.selfTd) > 0
        ).collect(Collectors.toList());

        if (filtered.size() > 0) {
            Random r = new Random(System.currentTimeMillis());
            for (int i = 0; i < 2; i++) {
                INode node = filtered.get(r.nextInt(filtered.size()));

                long from;
                if (node.getBestBlockNumber() >= selfNumber + 128) {
                    from = Math.max(1, selfNumber - backwardMin);
                } else if (node.getBestBlockNumber() >= selfNumber - 128) {
                    from = Math.max(1, selfNumber - backwardMax);
                } else {
                    // no need to request from this node. His TD is probably corrupted.
                    continue;
                }

                if (!ids.contains(node.getIdHash())) {
                    ids.add(node.getIdHash());
                    ReqBlocksHeaders rbh = new ReqBlocksHeaders(from, requestMax);

                    if (log.isDebugEnabled()) {
                        log.debug("<get-headers from-num={} size={} node={}>", from, requestMax, node.getIdShort());
                    }

                    this.p2p.send(node.getIdHash(), rbh);
                }
            }
        } else {
            log.debug("<get-headers find-no-valid-target>");
        }
    }
}
