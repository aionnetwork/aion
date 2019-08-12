package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.aion.base.AionTransaction;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.core.IRewardsCalculator;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.mcf.valid.BlockNumberRule;
import org.aion.mcf.valid.GrandParentBlockHeaderValidator;
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
    protected OptimizedEquiValidator getEquihashValidator() {
        if (this.equiValidator == null) {
            this.equiValidator = new OptimizedEquiValidator(CfgAion.getN(), CfgAion.getK());
        }
        return this.equiValidator;
    }

    @Override
    public BlockHeaderValidator createBlockHeaderValidator() {
        return new BlockHeaderValidator(
                Arrays.asList(
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule(),
                        new AionPOWRule(),
                        new EquihashSolutionRule(this.getEquihashValidator()),
                        new HeaderSealTypeRule()));
    }

    @Override
    public ParentBlockHeaderValidator createMiningParentHeaderValidator() {
        return new ParentBlockHeaderValidator(Collections.singletonList(new TimeStampRule()));
    }

    public GrandParentBlockHeaderValidator createGrandParentHeaderValidator() {
        return new GrandParentBlockHeaderValidator(
                Collections.singletonList(new AionDifficultyRule(this)));
    }

    @Override
    public IDifficultyCalculator getUnityDifficultyCalculator() {
        return unityDifficultyCalculator;
    }

    @Override
    public BlockHeaderValidator createStakingBlockHeaderValidator() {
        return new BlockHeaderValidator(
                Arrays.asList(
                        new HeaderSealTypeRule(),
                        new SignatureRule(),
                        new AionExtraDataRule(this.getConstants().getMaximumExtraDataSize()),
                        new EnergyConsumedRule()));
    }

    @Override
    public ParentBlockHeaderValidator createChainHeaderValidator() {
        return new ParentBlockHeaderValidator(Collections.singletonList(new BlockNumberRule()));
    }

    @Override
    public ParentBlockHeaderValidator createBlockParentHeaderValidator() {
        return new ParentBlockHeaderValidator(
                Collections.singletonList(
                        new EnergyLimitRule(
                                this.getConstants().getEnergyDivisorLimitLong(),
                                this.getConstants().getEnergyLowerBoundLong())));
    }

    @Override
    public ParentBlockHeaderValidator createStakingParentHeaderValidator() {
        return new ParentBlockHeaderValidator(
                Collections.singletonList(new StakingSeedRule()),
                Collections.singletonList(new StakingBlockTimeStampRule()));
    }

    public GrandParentBlockHeaderValidator createStakingGrandParentHeaderValidator() {
        return new GrandParentBlockHeaderValidator(
            Collections.singletonList(new unityDifficultyRule(this)));
    }

    // TODO : [unity] these 2 methods might move to the other proper class
    public static AionAddress getStakingContractAddress() {
        return stakingContractAddress;
    }

    public static ECKey getStakerKey() {
        return key;
    }
}
