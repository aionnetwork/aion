package org.aion.zero.impl.valid;

import static org.aion.util.biginteger.BIUtil.isEqual;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.IDifficultyCalculator;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;

public class UnityDifficultyRule implements GrandParentDependantBlockHeaderRule {

    private IDifficultyCalculator diffCalc;

    public UnityDifficultyRule(ChainConfiguration configuration) {
        //TODO: [unity] remove comments when we introduce the UnityDifficulty calculator
        //diffCalc = configuration.getUnityDifficultyCalculator();
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
                BlockHeaderValidatorUtil.addError(
                        "Invalid parent header type", this.getClass(), errors);
                return false;
            }

            if (grandParent != null && !(grandParent instanceof StakingBlockHeader)) {
                BlockHeaderValidatorUtil.addError(
                        "Invalid grandParent header type", this.getClass(), errors);
                return false;
            }
        } else if (current instanceof A0BlockHeader) {
            if (!(parent instanceof A0BlockHeader)) {
                BlockHeaderValidatorUtil.addError(
                        "Invalid parent header type", this.getClass(), errors);
                return false;
            }

            if (grandParent != null && !(grandParent instanceof A0BlockHeader)) {
                BlockHeaderValidatorUtil.addError(
                        "Invalid grandParent header type", this.getClass(), errors);
                return false;
            }
        } else {
            BlockHeaderValidatorUtil.addError("Invalid blockHeader type", this.getClass(), errors);
            return false;
        }

        BigInteger currDiff = current.getDifficultyBI();
        if (currDiff.signum() == 0) {
            return false;
        }

        if (parent.getNumber() == 0L) {
            if (!isEqual(parent.getDifficultyBI(), currDiff)) {
                BlockHeaderValidatorUtil.addError(
                        formatError(parent.getDifficultyBI(), currDiff), this.getClass(), errors);
                return false;
            }
            return true;
        }

        BigInteger calcDifficulty = this.diffCalc.calculateDifficulty(parent, grandParent);

        if (!isEqual(calcDifficulty, currDiff)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(calcDifficulty, currDiff), this.getClass(), errors);
            return false;
        }
        return true;
    }
}
