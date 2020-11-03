package org.aion.zero.impl.config;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Properties;

public class CfgFork {

    public CfgFork() {}

    public static final String FORK_PROPERTIES_PATH = "/fork.properties";

    private static Properties forkProperties = new Properties();

    private List<byte[]> fallbackTx;

    public void setProtocolUpgradeSettings(Properties _properties, List<byte[]> _fallbackTx) {
        forkProperties = _properties;
        fallbackTx = _fallbackTx;
    }

    public Properties getProperties() {
        return forkProperties;
    }

    public List<byte[]> getFallbackTx() {
        return fallbackTx;
    }

    @VisibleForTesting
    public void setFallbackTx(List<byte[]> _fallbackTx) {
        fallbackTx = _fallbackTx;
    }
}
