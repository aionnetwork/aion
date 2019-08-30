package org.aion.zero.impl.db;

import java.util.Properties;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.PruneConfig;

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
