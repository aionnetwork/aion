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

package org.aion.db.impl.mockdb;

import static org.aion.db.impl.DatabaseFactory.Props;

import java.util.Properties;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.IDriver;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Mock implementation of a key value database using a ConcurrentHashMap as our underlying
 * implementation, mostly for testing, when the Driver API interface is create, use this class as a
 * first mock implementation
 *
 * @author yao
 */
public class MockDBDriver implements IDriver {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;

    /** @inheritDoc */
    @Override
    public IByteArrayKeyValueDatabase connect(Properties info) {

        String dbType = info.getProperty(Props.DB_TYPE);
        String dbName = info.getProperty(Props.DB_NAME);

        if (!dbType.equals(this.getClass().getName())) {
            LOG.error("Invalid dbType provided: {}", dbType);
            return null;
        }

        return new MockDB(dbName);
    }

    /** @inheritDoc */
    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /** @inheritDoc */
    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }
}
