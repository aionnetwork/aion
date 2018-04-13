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
package org.aion.zero.impl.blockchain;

import org.aion.base.type.Address;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.mcf.mine.IMiner;
import org.aion.mcf.valid.*;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.zero.impl.core.RewardsCalculator;
import org.aion.zero.impl.valid.*;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Chain configuration handles the default parameters on a particular chain.
 * Also handles the default values for the chain genesis. In general these are
 * hardcoded and must be overridden by the genesis.json file if appropriate.
 *
 * @author yao
 *
 */
public class ChainConfiguration implements IChainCfg<IAionBlock, AionTransaction> {

    protected BlockConstants constants;
    protected IMiner<?, ?> miner;
    protected IDifficultyCalculator difficultyCalculatorAdapter;
    protected IRewardsCalculator rewardsCalculatorAdapter;
    protected OptimizedEquiValidator equiValidator;

    protected Address tokenBridgingOwnerAddress;

    public ChainConfiguration() {
        this(new BlockConstants());
    }

    public ChainConfiguration(BlockConstants constants) {
        this.constants = constants;
        DiffCalc diffCalcInternal = new DiffCalc(constants);
        RewardsCalculator rewardsCalcInternal = new RewardsCalculator(constants);

        this.difficultyCalculatorAdapter = (parent, grandParent) -> {
            // special case to handle the corner case for first block
            if (parent.getNumber() == 0L || parent.isGenesis()) {
                return parent.getDifficultyBI();
            }

            return diffCalcInternal.calcDifficultyTarget(
                BigInteger.valueOf(parent.getTimestamp()), BigInteger.valueOf(grandParent.getTimestamp()),
                parent.getDifficultyBI());
        };
        this.rewardsCalculatorAdapter = rewardsCalcInternal::calculateReward;
    }

    public IBlockConstants getConstants() {
        return constants;
    }

    public IBlockConstants getCommonConstants() {
        return getConstants();
    }

    public boolean acceptTransactionSignature(AionTransaction tx) {
        return true;
    }

    @Override
    public IDifficultyCalculator getDifficultyCalculator() {
        return difficultyCalculatorAdapter;
    }

    @Override
    public IRewardsCalculator getRewardsCalculator() {
        return rewardsCalculatorAdapter;
    }

    /**
     *
     * @return
     */
    protected OptimizedEquiValidator getEquihashValidator() {
        if (this.equiValidator == null) {
            this.equiValidator = new OptimizedEquiValidator(CfgAion.getN(), CfgAion.getK());
        }
        return this.equiValidator;
    }

    @Override
    public BlockHeaderValidator<A0BlockHeader> createBlockHeaderValidator() {
        return new BlockHeaderValidator<>(
                Arrays.asList(
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule(),
                        new AionPOWRule(),
                        new EquihashSolutionRule(this.getEquihashValidator()),
                        new AionHeaderVersionRule()
                ));
    }

    @Override
    public ParentBlockHeaderValidator<A0BlockHeader> createParentHeaderValidator() {
        return new ParentBlockHeaderValidator<>(
                Arrays.asList(
                        new BlockNumberRule<>(),
                        new TimeStampRule<>(),
                        new EnergyLimitRule(this.getConstants().getEnergyDivisorLimitLong(),
                            this.getConstants().getEnergyLowerBoundLong())
                ));
    }

    public GrandParentBlockHeaderValidator<A0BlockHeader> createGrandParentHeaderValidator() {
        return new GrandParentBlockHeaderValidator<>(
                Arrays.asList(
                        new AionDifficultyRule(this)
                ));
    }
}
