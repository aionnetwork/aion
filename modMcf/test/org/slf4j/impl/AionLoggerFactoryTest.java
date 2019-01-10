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
