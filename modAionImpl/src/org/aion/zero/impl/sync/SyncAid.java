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
 *
 */

package org.aion.zero.impl.sync;

import org.aion.zero.types.A0BlockHeader;
import java.math.BigInteger;
import java.util.*;

/**
 * @author chris
 * SyncAid is created to achive 2 tasks on sync process
 *    1. maintain particially trusted peers, proven by pow headers validations.
 *       Keep some degree of tolerance based on common best block. Process is
 *       driven by network status update
 *    2. maintain track points on re-sync process
 */
public class SyncAid {

    /**
     * group of partially trusted nodes
     * based on same common header
     */
    class Chain {

        List<A0BlockHeader> commonHeaders;

        /**
         * List<A0BlockHeader> of each entry contains headers starting from
         * next block headers from common header
         */
        Map<Integer, List<A0BlockHeader>> nodes = new HashMap<>();

    }

    /**
     * minimum block headers required to prove
     * to be added to group as partially trusted nodes
     */
    private final static int HEADERS_LEN = 20;

    /**
     * key - number from common header
     */
    private Map<Long, Chain> chains = new HashMap<>();

    /**
     * key - node id
     * value - total difficulty
     * a collection temporarily stores records
     * of node id paired with node`s total difficulty
     * updated by ResStatus
     */
    private Map<Integer, BigInteger> candicates = new HashMap<>();

    /**
     * 1. snapshop of truested peers ids of top group
     *    based on total difficulty
     * 2. update on network status update
     */
    private List<Integer> snapshot = new ArrayList<>();

    private void updateSnapshot(){

    }

    /**
     * @param _nodeId int
     * @param _latestHeaders List
     */
    public void update(int _nodeId, final List<A0BlockHeader> _latestHeaders){


        if(candicates.containsKey(_nodeId))

        // check size
        if(_latestHeaders.size() != HEADERS_LEN)
            return;

        // check if sorted and sequential
        _latestHeaders.sort((h1, h2) -> (int) (h1.getNumber() - h2.getNumber()));
        boolean ifSequential = true;
        for(int i = 0; i < HEADERS_LEN; i ++){
            if(i > 0 && _latestHeaders.get(i).getNumber() != (_latestHeaders.get(i - 1).getNumber() + 1))
                ifSequential = false;
        }
        if(!ifSequential)
            return;

        synchronized (chains){
            boolean found = false;
            for(Map.Entry<Long, Chain> entry : this.chains.entrySet()){
                if(entry.getValue().nodes.keySet().contains(_nodeId)) {

                    /*
                     * existing group
                     */
                    found = true;


                    break;
                }
            }

            if(!found){
                /*
                 * new group - folk
                 */
            }
        }
    }

    /**
     * @return int
     * random node id from top chain
     * used by sync mgr
     */
    int getRandom() {
        if(snapshot.size() == 0)
            return 0;
        else {
            Random r = new Random(System.currentTimeMillis());
            return snapshot.get(r.nextInt(snapshot.size()));
        }
    }
}
