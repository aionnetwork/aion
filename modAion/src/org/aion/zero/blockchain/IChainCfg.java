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

package org.aion.zero.blockchain;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.zero.core.IDifficultyCalculator;
import org.aion.zero.core.IRewardsCalculator;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.valid.ParentBlockHeaderValidator;

/**
 * Chain configuration interface.
 */
public interface IChainCfg<Blk extends IBlock<?, ?>, Tx extends ITransaction> {

    boolean acceptTransactionSignature(Tx tx);

    IBlockConstants getConstants();

    IBlockConstants getCommonConstants();

    IDifficultyCalculator getDifficultyCalculator();

    IRewardsCalculator getRewardsCalculator();

    BlockHeaderValidator createBlockHeaderValidator();

    ParentBlockHeaderValidator createParentHeaderValidator();
}
