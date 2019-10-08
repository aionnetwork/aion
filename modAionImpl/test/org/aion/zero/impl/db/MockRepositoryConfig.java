package org.aion.zero.impl.db;

import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.config.PruneConfig;

public class MockRepositoryConfig implements RepositoryConfig {

    private DBVendor vendor = DBVendor.MOCKDB;
    private PruneConfig pruneConfig = new CfgPrune(false);

    @Override
    public String getDbPath() {
        return "";
    }

    @Override
    public PruneConfig getPruneConfig() {
        return pruneConfig;
    }

    @Override
    public Properties getDatabaseConfig(String db_name) {
        Properties props = new Properties();
        props.setProperty(DatabaseFactory.Props.DB_TYPE, vendor.toValue());
        return props;
    }

    public MockRepositoryConfig(DBVendor vendor) {
        this.vendor = vendor;
    }

    public MockRepositoryConfig(PruneConfig _pruneConfig) {
        this.pruneConfig = _pruneConfig;
    }
}
