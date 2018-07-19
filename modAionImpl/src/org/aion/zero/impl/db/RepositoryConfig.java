/* ******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.zero.impl.db;

import java.util.Map;
import java.util.Properties;
import org.aion.base.db.DetailsProvider;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.mcf.config.CfgDb;

public class RepositoryConfig implements IRepositoryConfig {

    private final String dbPath;
    private final IPruneConfig cfgPrune;
    private final DetailsProvider detailsProvider;
    private final Map<String, Properties> cfg;

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @Override
    public IPruneConfig getPruneConfig() {
        return cfgPrune;
    }

    @Override
    public IContractDetails contractDetailsImpl() {
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

    public RepositoryConfig(
            final String dbPath, final DetailsProvider detailsProvider, final CfgDb cfgDb) {
        this.dbPath = dbPath;
        this.detailsProvider = detailsProvider;
        this.cfg = cfgDb.asProperties();
        this.cfgPrune = cfgDb.getPrune();
    }
}
