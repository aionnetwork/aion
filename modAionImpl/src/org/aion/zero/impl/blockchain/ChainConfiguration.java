package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
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
import org.aion.zero.impl.valid.FutureBlockRule;
import org.aion.zero.impl.valid.GrandParentBlockHeaderValidator;
import org.aion.zero.impl.valid.GrandParentDependantBlockHeaderRule;
import org.aion.zero.impl.valid.HeaderSealTypeRule;
import org.aion.zero.impl.valid.ParentBlockHeaderValidator;
import org.aion.zero.impl.valid.ParentOppositeTypeRule;
import org.aion.zero.impl.valid.SignatureRule;
import org.aion.zero.impl.valid.StakingBlockTimeStampRule;
import org.aion.zero.impl.valid.StakingSeedRule;
import org.aion.zero.impl.valid.TimeStampRule;
import org.aion.zero.impl.api.BlockConstants;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.zero.impl.core.RewardsCalculator;
import org.aion.zero.impl.valid.AionDifficultyRule;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.AionPOWRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.impl.valid.EquihashSolutionRule;
import org.aion.zero.impl.valid.UnityDifficultyRule;

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
    protected IDifficultyCalculator unityDifficultyCalculator;
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

        unityDifficultyCalculator =
            (parent, grandParent) -> {
                // special case to handle the corner case for first block
                if (parent.getNumber() == 0L || parent.isGenesis()) {
                    return parent.getDifficultyBI();
                }

                return unityCalc.calcDifficulty(parent, grandParent);
            };
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

    private OptimizedEquiValidator getEquihashValidator() {
        if (this.equiValidator == null) {
            this.equiValidator = new OptimizedEquiValidator(CfgAion.getN(), CfgAion.getK());
        }
        return this.equiValidator;
    }

    public BlockHeaderValidator createBlockHeaderValidator() {
        List<BlockHeaderRule> powRules =
                Arrays.asList(
                        new HeaderSealTypeRule(),
                        new FutureBlockRule(),
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule(),
                        new AionPOWRule(),
                        new EquihashSolutionRule(this.getEquihashValidator()));

        List<BlockHeaderRule> posRules =
                Arrays.asList(
                        new HeaderSealTypeRule(),
                        new FutureBlockRule(),
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule(),
                        new SignatureRule());

        Map<BlockSealType, List<BlockHeaderRule>> unityRules = new EnumMap<>(BlockSealType.class);
        unityRules.put(BlockSealType.SEAL_POW_BLOCK, powRules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK, posRules);

        return new BlockHeaderValidator(unityRules);
    }

    public ParentBlockHeaderValidator createSealParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> posRules =
                Arrays.asList(new StakingSeedRule(), new StakingBlockTimeStampRule());

        Map<BlockSealType, List<DependentBlockHeaderRule>> unityRules = new EnumMap<>(BlockSealType.class);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK, posRules);

        return new ParentBlockHeaderValidator(unityRules);
    }

    public GrandParentBlockHeaderValidator createPreUnityGrandParentHeaderValidator() {

        List<GrandParentDependantBlockHeaderRule> powRules =
                Collections.singletonList(new AionDifficultyRule(this));

        Map<BlockSealType, List<GrandParentDependantBlockHeaderRule>> unityRules = new EnumMap<>(BlockSealType.class);
        unityRules.put(BlockSealType.SEAL_POW_BLOCK, powRules);

        return new GrandParentBlockHeaderValidator(unityRules);
    }

    public GrandParentBlockHeaderValidator createUnityGrandParentHeaderValidator() {

        // Unity fork require 2 kinds of difficulty rules for pow block.

        List<GrandParentDependantBlockHeaderRule> powRules =
                Collections.singletonList(new UnityDifficultyRule(this));

        List<GrandParentDependantBlockHeaderRule> posRules =
                Collections.singletonList(new UnityDifficultyRule(this));

        Map<BlockSealType, List<GrandParentDependantBlockHeaderRule>> unityRules = new EnumMap<>(BlockSealType.class);
        unityRules.put(BlockSealType.SEAL_POW_BLOCK, powRules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK, posRules);

        return new GrandParentBlockHeaderValidator(unityRules);
    }

    public ParentBlockHeaderValidator createPreUnityParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> rules =
                Arrays.asList(
                        new BlockNumberRule(),
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                getConstants().getEnergyDivisorLimitLong(),
                                getConstants().getEnergyLowerBoundLong()));

        Map<BlockSealType, List<DependentBlockHeaderRule>> unityRules = new EnumMap<>(BlockSealType.class);
        unityRules.put(BlockSealType.SEAL_POW_BLOCK, rules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK, rules);

        return new ParentBlockHeaderValidator(unityRules);
    }    
    
    public ParentBlockHeaderValidator createUnityParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> rules =
                Arrays.asList(
                        new BlockNumberRule(),
                        new ParentOppositeTypeRule(),
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                getConstants().getEnergyDivisorLimitLong(),
                                getConstants().getEnergyLowerBoundLong()));

        Map<BlockSealType, List<DependentBlockHeaderRule>> unityRules = new EnumMap<>(BlockSealType.class);
        unityRules.put(BlockSealType.SEAL_POW_BLOCK, rules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK, rules);

        return new ParentBlockHeaderValidator(unityRules);
    }

    public IDifficultyCalculator getUnityDifficultyCalculator() {
        return unityDifficultyCalculator;
    }
}
