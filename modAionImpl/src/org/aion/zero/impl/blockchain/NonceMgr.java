/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/

package org.aion.zero.impl.blockchain;

import org.aion.base.type.Address;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.collections4.map.LRUMap;

/**
 * Nonce manager , used by Blockchain impl and api impl. api use it when send
 * transaction. blockchain impl use it for validating tx from network.
 *
 */
public class NonceMgr {

    Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.name());

    // Map.Entry<BigInteger,BigInteger> : the first BI - expect Tx nonce, the
    // second BI - repo expect Tx nonce
    static private Map<Address, Map.Entry<BigInteger, BigInteger>> map = Collections
            .synchronizedMap(new LRUMap<>(10000, 100));

    static private AionRepositoryImpl repo;

    static private AionPendingStateImpl txpool;

    private static class NonceMgrHolder {
        private final static NonceMgr inst = new NonceMgr();
    }

    public static NonceMgr inst() {
        return NonceMgrHolder.inst;
    }

    private NonceMgr() {
        repo = AionRepositoryImpl.inst();
        txpool = AionPendingStateImpl.inst();
    }

    public synchronized BigInteger getNonce(Address addr) {
        return repo.getNonce(addr);
    }

    public synchronized BigInteger getNonceAndAdd(Address addr) {

        Map.Entry<BigInteger, BigInteger> noncePair = map.get(addr);
        BigInteger repoNonce = repo.getNonce(addr);

        if (noncePair == null) {
            noncePair = new AbstractMap.SimpleEntry<>(repoNonce, repoNonce);
            if (LOG.isDebugEnabled()) {
                LOG.debug("NonceMgr - getNonce: [{}] set to repo nonce: [{}]", addr.toString(), repoNonce.toString());
            }
        }

        BigInteger poolNonce = txpool.bestPoolNonce(addr);
        if (LOG.isDebugEnabled()) {
            LOG.debug("NonceMgr - getNonceAndAdd: ADDR [{}] REPO [{}] NM [{}] POOL [{}] ", addr.toString(),
                    noncePair.getValue().toString(), noncePair.getKey() == null ? "null" : noncePair.getKey().toString(),
                    poolNonce == null ? -1 : poolNonce.toString());
        }


        BigInteger nmExpectNonce = (poolNonce == null ? noncePair.getKey() : poolNonce.add(BigInteger.ONE));
        map.put(addr, new AbstractMap.SimpleEntry(nmExpectNonce.add(BigInteger.ONE), repoNonce));
        return nmExpectNonce;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public synchronized void flush() {

        Map<Address, Map.Entry<BigInteger, BigInteger>> newNonceMap = Collections.synchronizedMap(new HashMap<>());

        for (Map.Entry<Address, Map.Entry<BigInteger, BigInteger>> entry : map.entrySet()) {
            Address key = entry.getKey();
            Map.Entry<BigInteger, BigInteger> value = entry.getValue();

            BigInteger repoExpectNonce = repo.getNonce(key);
            BigInteger poolBestNonce = txpool.bestPoolNonce(key);

            if (LOG.isInfoEnabled()) {
                LOG.info("NonceMgr - flush: ADDR [{}] REPO [{}] NM [{}] POOL [{}] ", key.toString(),
                        repoExpectNonce.toString(), value.getKey().toString(),
                        poolBestNonce == null ? -1 : poolBestNonce.toString());
            }

            newNonceMap.put(key, new AbstractMap.SimpleEntry<>(poolBestNonce == null ? repoExpectNonce : poolBestNonce, repoExpectNonce));
        }

        map.putAll(newNonceMap);
    }

    public synchronized BigInteger getRepoNonce(Address addr) {
        if (map.get(addr) == null) {
            return repo.getNonce(addr);
        } else {
            return map.get(addr).getValue();
        }
    }
}
