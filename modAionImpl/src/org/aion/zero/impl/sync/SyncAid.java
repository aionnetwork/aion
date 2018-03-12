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

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chris
 *
 * SyncAid is created to achive 2 tasks on sync process
 * 1. maintain particially trusted peers, proven by pow headers validations.
 *    Keep some degree of tolerance based on common best block. Process is
 *    driven by network status update
 * 2. maintain track points on re-sync process
 *
 *
 */
public class SyncAid {

    /**
     * particially tructed node
     */
    class SyncNode{

        /**
         * maintain sequential headers starting from next one
         * based on common header
         * can be empty for most time
         */
        List<A0BlockHeader> cached = new ArrayList<>();

    }

    /**
     * group of particially tructed nodes
     * based on same common height
     */
    class SyncGroup {

        List<SyncNode> nodes = new ArrayList<>();

        A0BlockHeader commonHeader;

    }

    /**
     *
     */
    public void getRandomSyncPeer(){

    }

    /**
     * return most close point sync point tracked
     */
    public void getSyncPoint(){

    }

}
