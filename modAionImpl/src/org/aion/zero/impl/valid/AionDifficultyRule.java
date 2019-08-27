package org.aion.zero.impl.valid;

import static org.aion.util.biginteger.BIUtil.isEqual;

import java.math.BigInteger;
import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.valid.GrandParentDependantBlockHeaderRule;
import org.aion.zero.impl.blockchain.ChainConfiguration;

/** Checks block's difficulty against calculated difficulty value */
public class AionDifficultyRule extends GrandParentDependantBlockHeaderRule {

    private IDifficultyCalculator diffCalc;

    public AionDifficultyRule(ChainConfiguration configuration) {
        this.diffCalc = configuration.getDifficultyCalculator();
    }

    /**
     * @inheritDoc
     * @implNote There is a special case in block 1 where we do not have a grandparent, to get
     *     around this we must apply a different rule.
     *     <p>Currently that rule will be defined to "pass on" the difficulty of the parent block
     *     {@code block 0} to the current block {@code block 1} If the current Header has invalid
     *     difficulty length, will return {BigInteger.ZERO}.
     */
    @Override
    public boolean validate(
            BlockHeader grandParent,
            BlockHeader parent,
            BlockHeader current,
            List<RuleError> errors) {

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

    private static String formatError(BigInteger expectedDifficulty, BigInteger actualDifficulty) {
        return "difficulty ("
                + actualDifficulty
                + ") != expected difficulty ("
                + expectedDifficulty
                + ")";
    }
}
