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
import org.aion.base.util.BIUtil;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.zero.impl.core.RewardsCalculator;
import org.aion.zero.impl.valid.*;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.equihash.EquiValidator;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.mine.IMiner;
import org.aion.mcf.valid.*;

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
    protected EquiValidator equiValidator;
    protected Address tokenBridgingOwnerAddress;

    public ChainConfiguration() {
        this(new BlockConstants());
    }

    public ChainConfiguration(BlockConstants constants) {
        this.constants = constants;
        DiffCalc diffCalcInternal = new DiffCalc(constants);
        RewardsCalculator rewardsCalcInternal = new RewardsCalculator(constants);

        // adapter class, use this for now because we don't know which
        // difficulty
        // algorithm to select
        this.difficultyCalculatorAdapter = (current, parent) -> diffCalcInternal.calcDifficultyTarget(
                BigInteger.valueOf(current.getTimestamp()), BigInteger.valueOf(parent.getTimestamp()),
                parent.getDifficultyBI());

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
    protected EquiValidator getEquihashValidator() {
        if (this.equiValidator == null) {
            this.equiValidator = new EquiValidator(CfgAion.getN(), CfgAion.getK());
        }
        return this.equiValidator;
    }

    @Override
    public BlockHeaderValidator<A0BlockHeader> createBlockHeaderValidator() {
        return new BlockHeaderValidator<A0BlockHeader>(Arrays.asList(
                new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()), new EnergyConsumedRule(),
                new AionPOWRule(), new EquihashSolutionRule(this.getEquihashValidator())));
    }

    @Override
    public ParentBlockHeaderValidator<A0BlockHeader> createParentHeaderValidator() {
        return new ParentBlockHeaderValidator<A0BlockHeader>(Arrays.asList(new BlockNumberRule<A0BlockHeader>(),
                new TimeStampRule<A0BlockHeader>(), new EnergyLimitRule(this.getConstants().getEnergyDivisorLimit(),
                        this.getConstants().getEnergyLowerBound())));
    }

    public static BigInteger FOUR = BigInteger.valueOf(4);
    public static BigInteger FIVE = BigInteger.valueOf(5);

    public long calcEnergyLimit(A0BlockHeader parentHeader) {
        // work primarily with BigIntegers to prevent overflow
        BigInteger parentEnergyLimit = BigInteger.valueOf(parentHeader.getEnergyLimit());
        BigInteger parentEnergyConsumed = BigInteger.valueOf(parentHeader.getEnergyConsumed());

        BigInteger increaseLowerBound = parentEnergyLimit.multiply(FOUR).divide(FIVE);

        if (parentEnergyConsumed.compareTo(increaseLowerBound) > 0) {
            BigInteger accValue = parentEnergyLimit.divide(this.getConstants().getEnergyDivisorLimit());
            return BIUtil.max(getConstants().getEnergyLowerBound(), parentEnergyLimit.add(accValue)).longValueExact();
        } else {
            return BIUtil.max(getConstants().getEnergyLowerBound(), parentEnergyLimit).longValueExact();
        }
    }
}
