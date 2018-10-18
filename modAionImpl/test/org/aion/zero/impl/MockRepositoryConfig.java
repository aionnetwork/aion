/*
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
 */
package org.aion.zero.impl;

import java.util.Properties;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.zero.impl.db.ContractDetailsAion;

public class MockRepositoryConfig implements IRepositoryConfig {

    private DBVendor vendor = DBVendor.MOCKDB;
    private IPruneConfig pruneConfig = new CfgPrune(false);

    public MockRepositoryConfig(DBVendor vendor) {
        this.vendor = vendor;
    }

    public MockRepositoryConfig(IPruneConfig _pruneConfig) {
        this.pruneConfig = _pruneConfig;
    }

    @Override
    public String getDbPath() {
        return "";
    }

    @Override
    public IPruneConfig getPruneConfig() {
        return pruneConfig;
    }

    @Override
    public IContractDetails contractDetailsImpl() {
        return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
    }

    @Override
    public Properties getDatabaseConfig(String db_name) {
        Properties props = new Properties();
        props.setProperty(DatabaseFactory.Props.DB_TYPE, vendor.toValue());
        props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
        return props;
    }
}
