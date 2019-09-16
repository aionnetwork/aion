package org.aion.zero.impl.valid;

import static org.aion.util.biginteger.BIUtil.isEqual;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.IDifficultyCalculator;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;

public class unityDifficultyRule extends GrandParentDependantBlockHeaderRule {

    private IDifficultyCalculator diffCalc;

    public unityDifficultyRule(ChainConfiguration configuration) {
        diffCalc = configuration.getUnityDifficultyCalculator();
    }

    private static String formatError(BigInteger expectedDifficulty, BigInteger actualDifficulty) {
        return "difficulty ("
                + actualDifficulty
                + ") != expected difficulty ("
                + expectedDifficulty
                + ")";
    }

    @Override
    public boolean validate(
            BlockHeader grandParent,
            BlockHeader parent,
            BlockHeader current,
            List<RuleError> errors) {

        if (current instanceof StakingBlockHeader) {
            if (!(parent instanceof StakingBlockHeader)) {
                throw new IllegalStateException("Invalid parent header input");
            }

            if (grandParent != null && !(grandParent instanceof StakingBlockHeader)) {
                throw new IllegalStateException("Invalid grandParent header input");
            }
        } else if (current instanceof A0BlockHeader) {
            if (!(parent instanceof A0BlockHeader)) {
                throw new IllegalStateException("Invalid parent header input");
            }

            if (grandParent != null && !(grandParent instanceof A0BlockHeader)) {
                throw new IllegalStateException("Invalid grandParent header input");
            }
        } else {
            throw  new IllegalStateException("Invalid BlockHeader Type!");
        }


        BigInteger currDiff = current.getDifficultyBI();
        if (currDiff.equals(BigInteger.ZERO)) {
            return false;
        }

        if (parent.getNumber() == 0L) {
            if (!isEqual(parent.getDifficultyBI(), currDiff)) {
                addError(formatError(parent.getDifficultyBI(), currDiff), errors);
                return false;
            }
            return true;
        }

        BigInteger calcDifficulty = this.diffCalc.calculateDifficulty(parent, grandParent);

        if (!isEqual(calcDifficulty, currDiff)) {
            addError(formatError(calcDifficulty, currDiff), errors);
            return false;
        }
        return true;
    }
}
