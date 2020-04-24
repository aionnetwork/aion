package org.aion.zero.impl.valid;

import static org.aion.util.biginteger.BIUtil.isEqual;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.IDifficultyCalculator;

public class UnityDifficultyRule implements GreatGrandParentDependantBlockHeaderRule {

    private IDifficultyCalculator diffCalc;

    public UnityDifficultyRule(ChainConfiguration configuration) {
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
            BlockHeader greatGrandParent,
            BlockHeader current,
            List<RuleError> errors) {

        BigInteger currDiff = current.getDifficultyBI();
        if (currDiff.signum() == 0) {
            return false;
        }

        if (greatGrandParent == null || grandParent == null) {
            return false;
        }

        BigInteger calcDifficulty = this.diffCalc.calculateDifficulty(grandParent, greatGrandParent);

        if (!isEqual(calcDifficulty, currDiff)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(calcDifficulty, currDiff), this.getClass(), errors);
            return false;
        }
        return true;
    }
}
