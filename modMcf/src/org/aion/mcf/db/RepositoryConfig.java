package org.aion.mcf.db;

import java.util.Properties;

/**
 * Represents a configuration interface accepted that should be accepted by the repository to
 * implement necessary configs
 *
 * @author yao
 */
public interface RepositoryConfig {

    /** @return absolute path to the DB folder containing files */
    String getDbPath();

    PruneConfig getPruneConfig();

    ContractDetails contractDetailsImpl();

    Properties getDatabaseConfig(String db_name);
}
