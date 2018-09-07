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
 *     Centrys Inc. <https://centrys.io>
 */
package org.aion.mcf.consensus;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.consensus.strategy.IBlockAppenderStrategy;
import org.aion.mcf.consensus.strategy.IBlockCreatorStrategy;
import org.aion.mcf.consensus.strategy.IBlockPropagatorStrategy;
import org.aion.mcf.consensus.strategy.IBlockValidatorStrategy;
import org.aion.mcf.core.ImportResult;

import java.util.List;

public class AbstractConsensus<BLK extends IBlock, TX extends ITransaction> implements IConsensus<BLK, TX>{
    private IBlockCreatorStrategy<BLK, TX> creator;
    private IBlockValidatorStrategy<BLK> validator;
    private IBlockAppenderStrategy<BLK> appender;
    private IBlockPropagatorStrategy<BLK> propagator;

    public AbstractConsensus(IBlockCreatorStrategy<BLK, TX> creator,
                             IBlockValidatorStrategy<BLK> validator,
                             IBlockAppenderStrategy<BLK> appender,
                             IBlockPropagatorStrategy<BLK> propagator) {
        this.creator = creator;
        this.validator = validator;
        this.appender = appender;
        this.propagator = propagator;
    }

    @Override
    public BLK create(BLK parent, List<TX> transactions) {
        return creator.execute(parent, transactions);
    }

    @Override
    public ImportResult append(BLK block) {
        return appender.execute(block);
    }

    @Override
    public Boolean validate(BLK block) {
        return validator.execute(block);
    }

    @Override
    public void propagateNewBlock(BLK block) {
        propagator.execute(block);
    }
}
