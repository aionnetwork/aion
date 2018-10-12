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
package org.aion.net;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.util.ByteArrayWrapper;

/**
 * Peer is a class intended to represent application specific information about a network node,
 * whereas {@link org.aion.p2p.impl.Node} only records and stores network information about a node.
 */
public class Peer {

    private static final int MAX_BLOCKS = 2048;
    private static final int MAX_TXS = 65536;

    private final Set<ByteArrayWrapper> seenBlocks = new LinkedHashSet<>();

    private final Set<ByteArrayWrapper> seenTxs = new LinkedHashSet<>();

    private final ReadWriteLock rwBlockLock = new ReentrantReadWriteLock();

    private final ReadWriteLock rwTxLock = new ReentrantReadWriteLock();

    /**
     * Metrics regarding application specific context (and some network context)
     */

    public Peer() {
    }

    public boolean addBlockHash(ByteArrayWrapper blockHash) {
        rwBlockLock.readLock().lock();
        try {
            if (seenBlocks.contains(blockHash)) {
                return false;
            }
        } finally {
            rwBlockLock.readLock().unlock();
        }

        rwBlockLock.writeLock().lock();
        try {
            seenBlocks.add(blockHash);
            if (seenBlocks.size() > MAX_BLOCKS) {
                seenBlocks.iterator().remove();
            }
            return true;
        } finally {
            rwBlockLock.writeLock().unlock();
        }
    }

    public boolean addTxHash(ByteArrayWrapper txHash) {
        rwTxLock.readLock().lock();
        try {
            if (seenTxs.contains(txHash)) {
                return false;
            }
        } finally {
            rwTxLock.readLock().unlock();
        }

        rwTxLock.writeLock().lock();
        try {
            seenTxs.add(txHash);
            if (seenTxs.size() > MAX_TXS) {
                seenTxs.iterator().remove();
            }
            return true;
        } finally {
            rwTxLock.writeLock().unlock();
        }
    }

    public boolean containsBlock(ByteArrayWrapper blockHash) {
        rwBlockLock.readLock().lock();
        try {
            return this.seenBlocks.contains(blockHash);
        } finally {
            rwBlockLock.readLock().unlock();
        }
    }

    public boolean containsTx(ByteArrayWrapper txHash) {
        rwTxLock.readLock().lock();
        try {
            return this.seenTxs.contains(txHash);
        } finally {
            rwTxLock.readLock().unlock();
        }
    }


    // TODO: implementation of serialization, so we can store this
    public byte[] getEncoded() {
        return null;
    }
}
