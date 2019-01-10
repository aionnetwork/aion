package org.aion.mcf.config;

import java.util.Properties;

public class CfgFork {

    public CfgFork() {}

    public static final String FORK_PROPERTIES_PATH = "/fork.properties";

    private static Properties forkProperties = new Properties();

    public void setProperties(Properties properties) {
        forkProperties = properties;
    }

    public Properties getProperties() {
        return forkProperties;
    }
}
