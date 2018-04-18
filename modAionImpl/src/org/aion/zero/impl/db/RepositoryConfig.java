/*******************************************************************************
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
 *
 ******************************************************************************/

package org.aion.zero.impl.db;

import org.aion.base.db.DetailsProvider;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryConfig;
import org.aion.mcf.config.CfgDb;

import java.util.Map;
import java.util.Properties;

public class RepositoryConfig implements IRepositoryConfig {

    private final String dbPath;
    private final int prune;
    private final DetailsProvider detailsProvider;
    private final Map<String, Properties> cfg;

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @Override
    public int getPrune() {
        return prune;
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

    public RepositoryConfig(final String dbPath,
                            final int prune,
                            final DetailsProvider detailsProvider,
                            final CfgDb cfgDb) {
        this.dbPath = dbPath;
        this.prune = prune;
        this.detailsProvider = detailsProvider;
        this.cfg = cfgDb.asProperties();
    }
}
