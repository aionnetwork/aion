package org.aion.zero.impl.db;

import java.util.Map;
import java.util.Properties;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.DetailsProvider;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.mcf.config.CfgDb;

public class RepositoryConfigImpl implements RepositoryConfig {

    private final String dbPath;
    private final PruneConfig cfgPrune;
    private final DetailsProvider detailsProvider;
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
    public ContractDetails contractDetailsImpl() {
        return this.detailsProvider.getDetails();
    }

    @Override
    public Properties getDatabaseConfig(String db_name) {
        Properties prop = cfg.get(db_name);
        if (prop == null) {
            prop = cfg.get(CfgDb.Names.DEFAULT);
        }
        return new Properties(prop);
    }

    public RepositoryConfigImpl(
            final String dbPath, final DetailsProvider detailsProvider, final CfgDb cfgDb) {
        this.dbPath = dbPath;
        this.detailsProvider = detailsProvider;
        this.cfg = cfgDb.asProperties();
        this.cfgPrune = (PruneConfig) cfgDb.getPrune();
    }
}
