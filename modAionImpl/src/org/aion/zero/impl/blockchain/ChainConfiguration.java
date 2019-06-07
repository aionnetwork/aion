package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.aion.types.AionAddress;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.mcf.mine.IMiner;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.valid.BlockNumberRule;
import org.aion.mcf.valid.GrandParentBlockHeaderValidator;
import org.aion.mcf.valid.ParentBlockHeaderValidator;
import org.aion.mcf.valid.TimeStampRule;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.zero.impl.core.RewardsCalculator;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.valid.AionDifficultyRule;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.AionHeaderVersionRule;
import org.aion.zero.impl.valid.AionPOWRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.impl.valid.EquihashSolutionRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;

/**
 * Chain configuration handles the default parameters on a particular chain. Also handles the
 * default values for the chain genesis. In general these are hardcoded and must be overridden by
 * the genesis.json file if appropriate.
 *
 * @author yao
 */
public class ChainConfiguration implements IChainCfg<AionBlock, AionTransaction> {

    protected BlockConstants constants;
    protected IMiner<?, ?> miner;
    protected IDifficultyCalculator difficultyCalculatorAdapter;
    protected IRewardsCalculator rewardsCalculatorAdapter;
    protected OptimizedEquiValidator equiValidator;

    protected AionAddress tokenBridgingOwnerAddress;

    public ChainConfiguration(final Long monetaryUpdateBlkNum, final BigInteger initialSupply) {
        this(new BlockConstants(), monetaryUpdateBlkNum, initialSupply);
    }

    public ChainConfiguration() {
        this(new BlockConstants(), null, BigInteger.ZERO);
    }

    public ChainConfiguration(BlockConstants constants, Long monetaryUpdateBlkNum, BigInteger initialSupply) {
        this.constants = constants;
        DiffCalc diffCalcInternal = new DiffCalc(constants);


        RewardsCalculator rewardsCalcInternal = new RewardsCalculator(constants, monetaryUpdateBlkNum, initialSupply);

        this.difficultyCalculatorAdapter =
                (parent, grandParent) -> {
                    // special case to handle the corner case for first block
                    if (parent.getNumber() == 0L || parent.isGenesis()) {
                        return parent.getDifficultyBI();
                    }

                    return diffCalcInternal.calcDifficultyTarget(
                            BigInteger.valueOf(parent.getTimestamp()),
                            BigInteger.valueOf(grandParent.getTimestamp()),
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

    /** @return */
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
                        new AionHeaderVersionRule()));
    }

    @Override
    public ParentBlockHeaderValidator<A0BlockHeader> createParentHeaderValidator() {
        return new ParentBlockHeaderValidator<>(
                Arrays.asList(
                        new BlockNumberRule<>(),
                        new TimeStampRule<>(),
                        new EnergyLimitRule(
                                this.getConstants().getEnergyDivisorLimitLong(),
                                this.getConstants().getEnergyLowerBoundLong())));
    }

    public GrandParentBlockHeaderValidator<A0BlockHeader> createGrandParentHeaderValidator() {
        return new GrandParentBlockHeaderValidator<>(
            Collections.singletonList(new AionDifficultyRule(this)));
    }
}
