package org.aion.zero.impl.blockchain;

import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class AionFactory {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    public static IAionChain create() {
        return AionImpl.inst();
    }

    public static Logger getLog() {
        return LOG;
    }
}
