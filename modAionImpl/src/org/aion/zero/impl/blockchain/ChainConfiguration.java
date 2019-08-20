package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.crypto.ECKey;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.mcf.mine.IMiner;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.AbstractBlockHeader.BlockSealType;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.valid.BlockNumberRule;
import org.aion.mcf.valid.DependentBlockHeaderRule;
import org.aion.mcf.valid.GrandParentBlockHeaderValidator;
import org.aion.mcf.valid.GrandParentDependantBlockHeaderRule;
import org.aion.mcf.valid.ParentBlockHeaderValidator;
import org.aion.mcf.valid.TimeStampRule;
import org.aion.types.AionAddress;
import org.aion.zero.impl.api.BlockConstants;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.zero.impl.core.RewardsCalculator;
import org.aion.zero.impl.core.UnityBlockDiffCalculator;
import org.aion.zero.impl.valid.AionDifficultyRule;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.HeaderSealTypeRule;
import org.aion.zero.impl.valid.AionPOWRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.impl.valid.EquihashSolutionRule;
import org.aion.zero.impl.valid.SignatureRule;
import org.aion.zero.impl.valid.StakingBlockTimeStampRule;
import org.aion.zero.impl.valid.unityDifficultyRule;
import org.aion.zero.impl.valid.StakingSeedRule;

/**
 * Chain configuration handles the default parameters on a particular chain. Also handles the
 * default values for the chain genesis. In general these are hardcoded and must be overridden by
 * the genesis.json file if appropriate.
 *
 * @author yao
 */
public class ChainConfiguration implements IChainCfg {

    protected BlockConstants constants;
    protected IDifficultyCalculator difficultyCalculatorAdapter;
    protected IDifficultyCalculator unityDifficultyCalculator;
    protected IRewardsCalculator rewardsCalculatorAdapter;
    protected OptimizedEquiValidator equiValidator;

    private static ECKey key = CfgAion.inst().getConsensus().getStakerKey();

    private static AionAddress stakingContractAddress =
            new AionAddress(
                    ByteUtil.hexStringToBytes(
                            "a056337bb14e818f3f53e13ab0d93b6539aa570cba91ce65c716058241989be9"));

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
    private OptimizedEquiValidator getEquihashValidator() {
        if (this.equiValidator == null) {
            this.equiValidator = new OptimizedEquiValidator(CfgAion.getN(), CfgAion.getK());
        }
        return this.equiValidator;
    }

    @Override
    public BlockHeaderValidator createBlockHeaderValidator() {
        List<BlockHeaderRule> powRules = Arrays.asList(
            new HeaderSealTypeRule(),
            new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
            new EnergyConsumedRule(),
            new AionPOWRule(),
            new EquihashSolutionRule(this.getEquihashValidator()));

        List<BlockHeaderRule> posRules = Arrays.asList(
            new HeaderSealTypeRule(),
            new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
            new EnergyConsumedRule(),
            new SignatureRule());

        Map<Byte, List<BlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK.getSealId(), posRules);

        return new BlockHeaderValidator(unityRules);
    }

    @Override
    public ParentBlockHeaderValidator createSealParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> posRules = Arrays.asList(
            new StakingSeedRule(),
            new StakingBlockTimeStampRule());

        Map<Byte, List<DependentBlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POS_BLOCK.getSealId(), posRules);

        return new ParentBlockHeaderValidator(unityRules);
    }

    public GrandParentBlockHeaderValidator createPreUnityGrandParentHeaderValidator() {

        List<GrandParentDependantBlockHeaderRule> powRules = Collections.singletonList(
            new AionDifficultyRule(this));

        Map<Byte, List<GrandParentDependantBlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);

        return new GrandParentBlockHeaderValidator(unityRules);
    }

    public GrandParentBlockHeaderValidator createUnityGrandParentHeaderValidator() {

        //Unity fork require 2 kinds of difficulty rules for pow block.

        List<GrandParentDependantBlockHeaderRule> powRules = Collections.singletonList(
            new unityDifficultyRule(this));

        List<GrandParentDependantBlockHeaderRule> posRules = Collections.singletonList(
            new unityDifficultyRule(this));

        Map<Byte, List<GrandParentDependantBlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK.getSealId(), posRules);

        return new GrandParentBlockHeaderValidator(unityRules);
    }

    @Override
    public ParentBlockHeaderValidator createChainParentBlockHeaderValidator() {
        List<DependentBlockHeaderRule> powRules =
            Arrays.asList(
                new BlockNumberRule(),
                new TimeStampRule(),
                new EnergyLimitRule(
                    getConstants().getEnergyDivisorLimitLong(),
                    getConstants().getEnergyLowerBoundLong()));

        List<DependentBlockHeaderRule> posRules =
            Arrays.asList(
                new BlockNumberRule(),
                new TimeStampRule(),
                new EnergyLimitRule(
                    getConstants().getEnergyDivisorLimitLong(),
                    getConstants().getEnergyLowerBoundLong()));

        Map<Byte, List<DependentBlockHeaderRule>> unityRules= new HashMap<>();
        unityRules.put(BlockSealType.SEAL_POW_BLOCK.getSealId(), powRules);
        unityRules.put(BlockSealType.SEAL_POS_BLOCK.getSealId(), posRules);

        return new ParentBlockHeaderValidator(unityRules);
    }

    @Override
    public IDifficultyCalculator getUnityDifficultyCalculator() {
        return unityDifficultyCalculator;
    }

    // TODO : [unity] these 2 methods might move to the other proper class
    public static AionAddress getStakingContractAddress() {
        return stakingContractAddress;
    }

    public static ECKey getStakerKey() {
        return key;
    }
}
