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

package org.slf4j.impl;

import static org.junit.Assert.assertNotNull;

import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

public class AionLoggerFactoryTest {

    @Test
    @Ignore
    public void test() {
        //		AionLogger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
        //		assertNotNull(LOG);
        //		assertNotNull(AionLogger.CONFIG_PARAMS);
    }

    public void test1() {
        Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
        assertNotNull(LOG);
        // assertNotNull(AionLogger.CONFIG_PARAMS);
    }
}
