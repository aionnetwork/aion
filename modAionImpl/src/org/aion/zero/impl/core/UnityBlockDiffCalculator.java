package org.aion.zero.impl.core;

import static org.aion.util.biginteger.BIUtil.max;

import java.math.BigInteger;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.zero.impl.api.BlockConstants;
import org.aion.zero.impl.types.GenesisStakingBlock;
import org.aion.util.math.FixedPoint;

public class UnityBlockDiffCalculator {
    private static FixedPoint difficultyIncreaseRate = FixedPoint.fromString("1.05");
    private static FixedPoint difficultyDecreaseRate = FixedPoint.fromString("0.952381");

    // Our barrier should be log2*10 = 6.9314718055994,
    // but we only compare it against integer values, so we use 7
    private static long barrier = 7;

    private BlockConstants constants;

    public UnityBlockDiffCalculator(BlockConstants _constants) {
        constants = _constants;
    }

    /*
     * @implNote The unity hardfork can not start before the block 4
     */
    public BigInteger calcDifficulty(BlockHeader grandParent, BlockHeader greatGrandParent) {

        BigInteger pd = grandParent.getDifficultyBI();
        
        if (grandParent.isGenesis() && grandParent.getSealType() == BlockSealType.SEAL_POS_BLOCK) {
            return pd;
        } else {
            long timeDelta = grandParent.getTimestamp() - greatGrandParent.getTimestamp();
            if (timeDelta < 1) {
                throw new IllegalStateException("Invalid parent timestamp & grandparent timestamp diff!");
            }

            BigInteger newDiff;
            if (timeDelta >= barrier) {
                newDiff = difficultyDecreaseRate.multiplyInteger(pd).toBigInteger();
            } else {
                newDiff = difficultyIncreaseRate.multiplyInteger(pd).toBigInteger();

                // Unity protocol, increasing one difficulty if the difficulty changes too small can not
                // be adjusted by the controlRate.
                if (newDiff.equals(pd)) {
                    newDiff = newDiff.add(BigInteger.ONE);
                }
            }

            if (grandParent.getSealType() == BlockSealType.SEAL_POS_BLOCK) {
                return max(GenesisStakingBlock.getGenesisDifficulty(), newDiff);
            } else if (grandParent.getSealType() == BlockSealType.SEAL_POW_BLOCK) {
                return max(constants.getMinimumDifficulty(), newDiff);
            } else {
                throw new IllegalStateException("Invalid block seal type!");
            }
        }
    }
}
