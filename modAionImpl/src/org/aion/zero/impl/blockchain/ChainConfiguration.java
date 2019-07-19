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
import org.aion.zero.impl.core.StakeBlockDiffCalculator;
import org.aion.zero.impl.valid.AionDifficultyRule;
import org.aion.zero.impl.valid.AionExtraDataRule;
import org.aion.zero.impl.valid.HeaderSealTypeRule;
import org.aion.zero.impl.valid.AionPOWRule;
import org.aion.zero.impl.valid.EnergyConsumedRule;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.impl.valid.EquihashSolutionRule;
import org.aion.zero.impl.valid.SignatureRule;
import org.aion.zero.impl.valid.StakingBlockTimeStampRule;
import org.aion.zero.impl.valid.StakingDifficultyRule;
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
    protected IDifficultyCalculator stakingDifficultyCalculator;
    protected IRewardsCalculator rewardsCalculatorAdapter;
    protected OptimizedEquiValidator equiValidator;

    // TODO: [unity] to implement the key reading/setting logic
    private static byte[] privateKey =
            ByteUtil.hexStringToBytes(
                    "0xcc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4069346ca77152d3e42b1630826feef365683038c3b00ff20b0ea42d7c121fa9f");

    private static ECKey key = ECKeyFac.inst().fromPrivate(privateKey);

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

        StakeBlockDiffCalculator stakeCalc = new StakeBlockDiffCalculator(constants);

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

        stakingDifficultyCalculator =
                (parent, grandParent) -> {
                    // special case to handle the corner case for first block
                    if (parent.getNumber() == 0L || parent.isGenesis()) {
                        return parent.getDifficultyBI();
                    }

                    return stakeCalc.calcDifficulty(parent, grandParent);
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
    public ParentBlockHeaderValidator createParentHeaderValidator() {
        return new ParentBlockHeaderValidator(
                Arrays.asList(
                        new TimeStampRule(),
                        new EnergyLimitRule(
                                this.getConstants().getEnergyDivisorLimitLong(),
                                this.getConstants().getEnergyLowerBoundLong())));
    }

    public GrandParentBlockHeaderValidator createGrandParentHeaderValidator() {
        return new GrandParentBlockHeaderValidator(
                Collections.singletonList(new AionDifficultyRule(this)));
    }

    @Override
    public IDifficultyCalculator getStakingDifficultyCalculator() {
        return stakingDifficultyCalculator;
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
    public ParentBlockHeaderValidator createStakingParentHeaderValidator() {
        return new ParentBlockHeaderValidator(
                Arrays.asList(
                        new StakingSeedRule(),
                        new EnergyLimitRule(
                                this.getConstants().getEnergyDivisorLimitLong(),
                                this.getConstants().getEnergyLowerBoundLong())),
                Collections.singletonList(new StakingBlockTimeStampRule()));
    }

    public GrandParentBlockHeaderValidator createStakingGrandParentHeaderValidator() {
        return new GrandParentBlockHeaderValidator(
            Collections.singletonList(new StakingDifficultyRule(this)));
    }

    // TODO : [unity] these 2 methods might move to the other proper class
    public static AionAddress getStakingContractAddress() {
        return stakingContractAddress;
    }

    public static ECKey getStakerKey() {
        return key;
    }
}
