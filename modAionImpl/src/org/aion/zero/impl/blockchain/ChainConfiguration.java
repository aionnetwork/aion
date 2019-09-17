package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.zero.impl.core.UnityBlockDiffCalculator;
import org.aion.zero.impl.types.IBlockConstants;
import org.aion.zero.impl.core.IDifficultyCalculator;
import org.aion.zero.impl.core.IRewardsCalculator;
import org.aion.zero.impl.valid.BlockHeaderRule;
import org.aion.zero.impl.valid.BlockHeaderValidator;
import org.aion.zero.impl.valid.BlockNumberRule;
import org.aion.zero.impl.valid.DependentBlockHeaderRule;
import org.aion.zero.impl.valid.GrandParentBlockHeaderValidator;
import org.aion.zero.impl.valid.GrandParentDependantBlockHeaderRule;
import org.aion.zero.impl.valid.ParentBlockHeaderValidator;
import org.aion.zero.impl.valid.TimeStampRule;
import org.aion.zero.impl.api.BlockConstants;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.zero.impl.core.RewardsCalculator;
import org.aion.zero.impl.valid.AionDifficultyRule;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.AionHeaderVersionRule;
import org.aion.zero.impl.valid.AionPOWRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.impl.valid.EquihashSolutionRule;

/**
 * Chain configuration handles the default parameters on a particular chain. Also handles the
 * default values for the chain genesis. In general these are hardcoded and must be overridden by
 * the genesis.json file if appropriate.
 *
 * @author yao
 */
public class ChainConfiguration {

    protected BlockConstants constants;
    protected IDifficultyCalculator difficultyCalculatorAdapter;
    protected IRewardsCalculator rewardsCalculatorAdapter;
    protected OptimizedEquiValidator equiValidator;

    public ChainConfiguration(final Long monetaryUpdateBlkNum, final BigInteger initialSupply) {
        this(new BlockConstants(), monetaryUpdateBlkNum, initialSupply);
    }

    public ChainConfiguration() {
        this(new BlockConstants(), null, BigInteger.ZERO);
    }

    public ChainConfiguration(
            BlockConstants constants, Long monetaryUpdateBlkNum, BigInteger initialSupply) {
        this.constants = constants;
        DiffCalc diffCalcInternal = new DiffCalc(constants);
        UnityBlockDiffCalculator unityCalc = new UnityBlockDiffCalculator(constants);

        RewardsCalculator rewardsCalcInternal =
                new RewardsCalculator(constants, monetaryUpdateBlkNum, initialSupply);

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
    public IDifficultyCalculator getDifficultyCalculator() {
        return difficultyCalculatorAdapter;
    }

    public IRewardsCalculator getRewardsCalculator() {
        return rewardsCalculatorAdapter;
    }

    protected OptimizedEquiValidator getEquihashValidator() {
        if (this.equiValidator == null) {
            this.equiValidator = new OptimizedEquiValidator(CfgAion.getN(), CfgAion.getK());
        }
        return this.equiValidator;
    }

    public BlockHeaderValidator createBlockHeaderValidator() {
        List<BlockHeaderRule> powRules = Arrays.asList(
            new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
            new EnergyConsumedRule(),
            new AionPOWRule(),
            new EquihashSolutionRule(this.getEquihashValidator()),
            new AionHeaderVersionRule());

        Map<Byte, List<BlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);

        return new BlockHeaderValidator(unityRules);
    }

    public ParentBlockHeaderValidator createParentHeaderValidator() {
        List<DependentBlockHeaderRule> powRules =
                Arrays.asList(
                        new BlockNumberRule(),
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                this.getConstants().getEnergyDivisorLimitLong(),
                                this.getConstants().getEnergyLowerBoundLong()));

        Map<Byte, List<DependentBlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);
        return new ParentBlockHeaderValidator(unityRules);
    }

    public GrandParentBlockHeaderValidator createGrandParentHeaderValidator() {
        List<GrandParentDependantBlockHeaderRule> powRules =
            Collections.singletonList(new AionDifficultyRule(this));

        Map<Byte, List<GrandParentDependantBlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);
        return new GrandParentBlockHeaderValidator(unityRules);
    }
}
