package org.aion.zero.impl.blockchain;

import org.aion.log.AionLoggerFactory;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.config.CfgAion;

import java.util.HashMap;
import java.util.Map;

public class PoolDumpUtils {
    public PoolDumpUtils() {

    }

    public static void dumpPool() {
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionHub hub = AionHub.inst();

        hub.getPendingState().DumpPool();
    }
}
