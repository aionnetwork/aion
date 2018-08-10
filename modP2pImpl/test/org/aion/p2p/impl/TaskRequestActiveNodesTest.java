/*
 * Copyright (c) 2017-2018 Aion foundation.
 *      This file is part of the aion network project.
 *
 *      The aion network project is free software: you can redistribute it
 *      and/or modify it under the terms of the GNU General Public License
 *      as published by the Free Software Foundation, either version 3 of
 *      the License, or any later version.
 *
 *      The aion network project is distributed in the hope that it will
 *      be useful, but WITHOUT ANY WARRANTY; without even the implied
 *      warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *      See the GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the aion network project source files.
 *      If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contributors:
 *      Aion foundation.
 */

package org.aion.p2p.impl;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

public class TaskRequestActiveNodesTest {

    @Mock
    private IP2pMgr mgr;

    @Mock
    private INode node;

    private Logger p2pLOG;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevel.TRACE.name());
        AionLoggerFactory.init(logMap);
        p2pLOG = AionLoggerFactory.getLogger(LogEnum.P2P.name());
    }

    @Test
    public void testRun() {
        when(mgr.getRandom()).thenReturn(node);

        TaskRequestActiveNodes tran = new TaskRequestActiveNodes(mgr, p2pLOG);
        assertNotNull(tran);
        tran.run();
    }
}
