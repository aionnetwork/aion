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
import org.aion.mcf.config.CfgDb;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.db.AionContractDetailsImpl;
import org.aion.zero.impl.config.CfgAion;

/**
 * Contract details provider for Aion.
 * 
 * @author gavin
 *
 */
public class ContractDetailsAion implements DetailsProvider {

    private final int prune;
    private final int memStorageLimit;

    private ContractDetailsAion() {
        // CfgDb cfgDb = CfgAion.inst().getDb();
        this.prune = 0; // cfgDb.getPrune();
        this.memStorageLimit = 64 * 1024; // cfgDb.getDetailsInMemoryStorageLimit();
    }

    /**
     * Non-Singleton constructor for class, currently this constructor is mostly
     * used by {@link AionRepositoryImpl} related testing. In the future when we
     * moved towards a more formal dependency management framework, this may
     * become more useful.
     *
     * @param prune
     *            a value > 0 indicates that prune should be for that many
     *            blocks.
     * @param memStorageLimit
     *            indicates the maximum storage size (is this used?)
     */
    protected ContractDetailsAion(final int prune, final int memStorageLimit) {
        this.prune = prune;
        this.memStorageLimit = memStorageLimit;
    }

    /**
     * Static factory method for creating the details provider, as the name
     * indicates this is intended for use in testing.
     *
     * @param prune
     *            {@link ContractDetailsAion}
     * @param memStorageLimit
     *            {@link ContractDetailsAion}
     * @return {@code contractDetails} a standalone/new instance of contract
     *         details
     */
    public static ContractDetailsAion createForTesting(final int prune, final int memStorageLimit) {
        return new ContractDetailsAion(prune, memStorageLimit);
    }

    private static class ContractDetailsAionHolder {
        public static final ContractDetailsAion inst = new ContractDetailsAion();
    }

    public static ContractDetailsAion getInstance() {
        return ContractDetailsAionHolder.inst;
    }

    @Override
    public IContractDetails<DataWord> getDetails() {
        return new AionContractDetailsImpl(this.prune, this.memStorageLimit);
    }

}
