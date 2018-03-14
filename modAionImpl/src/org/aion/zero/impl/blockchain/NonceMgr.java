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
        if (noncePair == null) {
            BigInteger repoNonce = repo.getNonce(addr);
            noncePair = new AbstractMap.SimpleEntry<>(repoNonce, repoNonce);
            if (LOG.isDebugEnabled()) {
                LOG.debug("NonceMgr - getNonce: [{}] set to repo nonce: [{}]", addr.toString(), repoNonce.toString());
            }
        }

        BigInteger nmExpectNonce = noncePair.getKey();
        BigInteger repoExpectNonce = noncePair.getValue();
        Map.Entry<BigInteger, BigInteger> poolBestNonceSet = txpool.bestNonceSet(addr);

        if (LOG.isDebugEnabled()) {
            LOG.debug("NonceMgr - flush: ADDR [{}] REPO [{}] NM [{}] TX1 [{}] TX2 [{}]", addr.toString(),
                    noncePair.getValue().toString(), nmExpectNonce == null ? "null" : nmExpectNonce.toString(),
                    poolBestNonceSet == null ? -1 : poolBestNonceSet.getKey().toString(),
                    poolBestNonceSet == null ? -1 : poolBestNonceSet.getValue().toString());
        }

        if (nmExpectNonce == null) {
            nmExpectNonce = repo.getNonce(addr);
            repoExpectNonce = nmExpectNonce;

            if (LOG.isDebugEnabled()) {
                LOG.debug("NonceMgr - getNonce: [{}] set to repo nonce: [{}]", addr.toString(),
                        nmExpectNonce.toString());
            }

        }

        if (poolBestNonceSet != null) {
            BigInteger contiNonceStart = poolBestNonceSet.getKey();
            BigInteger contiNonceEnd = poolBestNonceSet.getValue();

            if (nmExpectNonce.compareTo(contiNonceStart) != -1) {
                if (nmExpectNonce.compareTo(contiNonceEnd) != 1) {
                    nmExpectNonce = contiNonceEnd.add(BigInteger.ONE);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("NonceMgr - getNonce: [{}] set to contiNonceEnd nonce+1 [{}]", addr.toString(),
                                nmExpectNonce.toString());
                    }

                }
            }
        }

        map.put(addr, new AbstractMap.SimpleEntry(nmExpectNonce.add(BigInteger.ONE), repoExpectNonce));
        return nmExpectNonce;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public synchronized void flush() {

        if (LOG.isTraceEnabled()) {
            LOG.trace("NonceMgr- flush start");
        }

        Map<Address, Map.Entry<BigInteger, BigInteger>> newNonceMap = Collections.synchronizedMap(new HashMap<>());

        for (Address addr : map.keySet()) {

            BigInteger repoExpectNonce = repo.getNonce(addr);
            int cmp = map.get(addr).getValue().compareTo(repoExpectNonce);

            if (LOG.isTraceEnabled()) {
                LOG.trace("NonceMgr- txpool.bestNonceSet");
            }

            Map.Entry<BigInteger, BigInteger> poolBestNonceSet = txpool.bestNonceSet(addr);

            if (LOG.isInfoEnabled()) {
                LOG.info("NonceMgr - flush: ADDR [{}] REPO [{}] NM [{}] TX1 [{}] TX2 [{}]", addr.toString(),
                        repoExpectNonce.toString(), map.get(addr).getKey().toString(),
                        poolBestNonceSet == null ? -1 : poolBestNonceSet.getKey().toString(),
                        poolBestNonceSet == null ? -1 : poolBestNonceSet.getValue().toString());
            }

            if (cmp == -1) {
                if (poolBestNonceSet != null) {
                    cmp = repoExpectNonce.compareTo(poolBestNonceSet.getKey());
                    if (cmp == -1) {
                        newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(repoExpectNonce, repoExpectNonce));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("NonceMgr - flush: [{}] set to repoExpectNonce [{}]", addr.toString(),
                                    repoExpectNonce.toString());
                        }
                    } else {
                        BigInteger contiNonceEnd = poolBestNonceSet.getValue();
                        cmp = repoExpectNonce.compareTo(contiNonceEnd);
                        if (cmp == 1) {
                            newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(repoExpectNonce, repoExpectNonce));
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("NonceMgr - flush: [{}] set to repoExpectNonce [{}]", addr.toString(),
                                        repoExpectNonce.toString());
                            }

                        } else {
                            newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(contiNonceEnd.add(BigInteger.ONE), repoExpectNonce));
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("NonceMgr - flush: [{}] set to contiNonceEnd+1 [{}]", addr.toString(),
                                        contiNonceEnd.add(BigInteger.ONE).toString());
                            }
                        }
                    }
                } else {
                    newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(repoExpectNonce, repoExpectNonce));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("NonceMgr - flush: [{}] set to repoExpectNonce [{}]", addr.toString(),
                                repoExpectNonce.toString());
                    }
                }
            } else {
                if (poolBestNonceSet != null) {
                    cmp = map.get(addr).getKey().compareTo(poolBestNonceSet.getKey());
                    if (cmp == -1) {
                        newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(repoExpectNonce, repoExpectNonce));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("NonceMgr - flush: [{}] set to repoExpectNonce [{}]", addr.toString(),
                                    repoExpectNonce.toString());
                        }
                    } else {
                        newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(poolBestNonceSet.getValue().add(BigInteger.ONE), repoExpectNonce));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("NonceMgr - flush: [{}] set to contiNonceEnd+1 [{}]", addr.toString(),
                                    poolBestNonceSet.getValue().add(BigInteger.ONE).toString());
                        }
                    }
                } else {
                    newNonceMap.put(addr, new AbstractMap.SimpleEntry<>(repoExpectNonce, repoExpectNonce));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("NonceMgr - flush: [{}] set to repoExpectNonce [{}]", addr.toString(),
                                repoExpectNonce.toString());
                    }
                }
            }
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
