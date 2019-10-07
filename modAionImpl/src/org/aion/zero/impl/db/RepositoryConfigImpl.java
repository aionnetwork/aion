package org.aion.zero.impl.db;

import java.util.Map;
import java.util.Properties;
import org.aion.zero.impl.config.CfgDb;
import org.aion.zero.impl.config.PruneConfig;

public class RepositoryConfigImpl implements RepositoryConfig {

    private final String dbPath;
    private final PruneConfig cfgPrune;
    private final Map<String, Properties> cfg;

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @Override
    public PruneConfig getPruneConfig() {
        return cfgPrune;
    }

    @Override
    public Properties getDatabaseConfig(String db_name) {
        Properties prop = cfg.get(db_name);
        if (prop == null) {
            prop = cfg.get(CfgDb.Names.DEFAULT);
        }
        return new Properties(prop);
    }

    public RepositoryConfigImpl(final String dbPath, final CfgDb cfgDb) {
        this.dbPath = dbPath;
        this.cfg = cfgDb.asProperties();
        this.cfgPrune = cfgDb.getPrune();
    }
}
