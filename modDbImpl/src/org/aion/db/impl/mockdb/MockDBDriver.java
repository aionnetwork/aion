package org.aion.db.impl.mockdb;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.IDriver;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.Properties;

import static org.aion.db.impl.DatabaseFactory.Props;

/**
 * Mock implementation of a key value database using a ConcurrentHashMap as our
 * underlying implementation, mostly for testing, when the Driver API interface
 * is create, use this class as a first mock implementation
 *
 * @author yao
 */
public class MockDBDriver implements IDriver {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;

    /**
     * @inheritDoc
     */
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

    /**
     * @inheritDoc
     */
    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }
}
