package org.aion.zero.impl.config;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Properties;

public class CfgFork {

    public CfgFork() {}

    public static final String FORK_PROPERTIES_PATH = "/fork.properties";

    private static Properties forkProperties = new Properties();

    private List<byte[]> rollbackTx;

    public void setProtocolUpgradeSettings(Properties _properties, List<byte[]> _rollback) {
        forkProperties = _properties;
        rollbackTx = _rollback;
    }

    public Properties getProperties() {
        return forkProperties;
    }

    public List<byte[]> getRollbackTx() {
        return rollbackTx;
    }

    @VisibleForTesting
    public void setRollbackTx(List<byte[]> _rollback) {
        rollbackTx = _rollback;
    }
}
