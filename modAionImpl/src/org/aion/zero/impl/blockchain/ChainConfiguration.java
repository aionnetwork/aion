package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.zero.impl.core.TimeVaryingRewardsCalculator;
import org.aion.zero.impl.types.BlockHeader.Seal;
import org.aion.zero.impl.core.RewardsCalculatorAfterUnity;
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
import org.aion.zero.impl.valid.GreatGrandParentBlockHeaderValidator;
import org.aion.zero.impl.valid.GreatGrandParentDependantBlockHeaderRule;
import org.aion.zero.impl.valid.HeaderSealTypeRule;
import org.aion.zero.impl.valid.ParentBlockHeaderValidator;
import org.aion.zero.impl.valid.ParentOppositeTypeRule;
import org.aion.zero.impl.valid.SignatureRule;
import org.aion.zero.impl.valid.StakingBlockTimeStampRule;
import org.aion.zero.impl.valid.StakingSeedCreationRule;
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
import org.aion.zero.impl.valid.VRFProofRule;

/**
 * Chain configuration handles the default parameters on a particular chain. Also handles the
 * default values for the chain genesis. In general these are hardcoded and must be overridden by
 * the genesis.json file if appropriate.
 *
 * @author yao
 */
public class ChainConfiguration {

    protected BlockConstants constants;
    protected IDifficultyCalculator preUnityDifficultyCalculator;
    protected IDifficultyCalculator unityDifficultyCalculator;
    protected IRewardsCalculator rewardsCalculatorAdapter;
    protected IRewardsCalculator rewardsCalculatorAfterUnity;
    protected IRewardsCalculator rewardsCalculatorAfterSignatureSchemeSwap;

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

        this.preUnityDifficultyCalculator =
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

        this.rewardsCalculatorAfterUnity = RewardsCalculatorAfterUnity::calculateReward;

        this.rewardsCalculatorAfterSignatureSchemeSwap = TimeVaryingRewardsCalculator::calculateReward;

        unityDifficultyCalculator = unityCalc::calcDifficulty;
    }

    public IBlockConstants getConstants() {
        return constants;
    }
    public IDifficultyCalculator getPreUnityDifficultyCalculator() {
        return preUnityDifficultyCalculator;
    }

    public IRewardsCalculator getRewardsCalculatorBeforeSignatureSchemeSwap(boolean isAfterUnityFork) {
        return isAfterUnityFork ? rewardsCalculatorAfterUnity : rewardsCalculatorAdapter;
    }

    public IRewardsCalculator getRewardsCalculatorAfterSignatureSchemeSwap(boolean isMiningBlock) {
        return isMiningBlock ? rewardsCalculatorAfterSignatureSchemeSwap : rewardsCalculatorAfterUnity;
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

        Map<Seal, List<BlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_WORK, powRules);
        unityRules.put(Seal.PROOF_OF_STAKE, posRules);

        return new BlockHeaderValidator(unityRules);
    }

    public BlockHeaderValidator createBlockHeaderValidatorForImport() {
        List<BlockHeaderRule> powRules =
                Arrays.asList(
                        new HeaderSealTypeRule(),
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule(),
                        new AionPOWRule(),
                        new EquihashSolutionRule(this.getEquihashValidator()));

        List<BlockHeaderRule> posRules =
                Arrays.asList(
                        new HeaderSealTypeRule(),
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule(),
                        new SignatureRule());

        Map<Seal, List<BlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_WORK, powRules);
        unityRules.put(Seal.PROOF_OF_STAKE, posRules);

        return new BlockHeaderValidator(unityRules);
    }

    public GrandParentBlockHeaderValidator createPreUnityGrandParentHeaderValidator() {

        List<GrandParentDependantBlockHeaderRule> powRules =
                Collections.singletonList(new AionDifficultyRule(this));

        Map<Seal, List<GrandParentDependantBlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_WORK, powRules);

        return new GrandParentBlockHeaderValidator(unityRules);
    }

    public GreatGrandParentBlockHeaderValidator createUnityGreatGrandParentHeaderValidator() {

        List<GreatGrandParentDependantBlockHeaderRule> powRules =
                Collections.singletonList(new UnityDifficultyRule(this));

        List<GreatGrandParentDependantBlockHeaderRule> posRules =
            Arrays.asList(new UnityDifficultyRule(this),
                new StakingSeedRule());

                Map<Seal, List<GreatGrandParentDependantBlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_WORK, powRules);
        unityRules.put(Seal.PROOF_OF_STAKE, posRules);

        return new GreatGrandParentBlockHeaderValidator(unityRules);
    }

    public GreatGrandParentBlockHeaderValidator createNonceSeedValidator() {

        List<GreatGrandParentDependantBlockHeaderRule> posRules = Collections.singletonList(new StakingSeedCreationRule());

        Map<Seal, List<GreatGrandParentDependantBlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_STAKE, posRules);

        return new GreatGrandParentBlockHeaderValidator(unityRules);
    }

    public GreatGrandParentBlockHeaderValidator createNonceSeedDifficultyValidator() {
        List<GreatGrandParentDependantBlockHeaderRule> posRules = Collections.singletonList(new UnityDifficultyRule(this));

        Map<Seal, List<GreatGrandParentDependantBlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_STAKE, posRules);

        return new GreatGrandParentBlockHeaderValidator(unityRules);
    }

    public ParentBlockHeaderValidator createPreUnityParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> rules =
                Arrays.asList(
                        new BlockNumberRule(),
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                getConstants().getEnergyDivisorLimitLong(),
                                getConstants().getEnergyLowerBoundLong()));

        Map<Seal, List<DependentBlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_WORK, rules);
        unityRules.put(Seal.PROOF_OF_STAKE, rules);

        return new ParentBlockHeaderValidator(unityRules);
    }    
    
    public ParentBlockHeaderValidator createUnityParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> PoWrules =
                Arrays.asList(
                        new BlockNumberRule(),
                        new ParentOppositeTypeRule(),
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                getConstants().getEnergyDivisorLimitLong(),
                                getConstants().getEnergyLowerBoundLong()));
        
        List<DependentBlockHeaderRule> PoSrules =
                Arrays.asList(
                        new BlockNumberRule(),
                        new ParentOppositeTypeRule(),
                        new StakingBlockTimeStampRule(),
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                getConstants().getEnergyDivisorLimitLong(),
                                getConstants().getEnergyLowerBoundLong()));

        Map<Seal, List<DependentBlockHeaderRule>> unityRules = new EnumMap<>(Seal.class);
        unityRules.put(Seal.PROOF_OF_WORK, PoWrules);
        unityRules.put(Seal.PROOF_OF_STAKE, PoSrules);

        return new ParentBlockHeaderValidator(unityRules);
    }

    public IDifficultyCalculator getUnityDifficultyCalculator() {
        return unityDifficultyCalculator;
    }

    public GrandParentBlockHeaderValidator createVRFValidator() {
        List<GrandParentDependantBlockHeaderRule> posRules =
            Collections.singletonList(new VRFProofRule());

        Map<Seal, List<GrandParentDependantBlockHeaderRule>> vrfProofRules = new EnumMap<>(Seal.class);
        vrfProofRules.put(Seal.PROOF_OF_STAKE, posRules);

        return new GrandParentBlockHeaderValidator(vrfProofRules);
    }
}
