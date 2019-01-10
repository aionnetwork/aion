package org.aion.zero.impl.valid;

import static org.aion.base.util.BIUtil.isEqual;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.mcf.valid.GrandParentDependantBlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;

/** Checks block's difficulty against calculated difficulty value */
public class AionDifficultyRule extends GrandParentDependantBlockHeaderRule<A0BlockHeader> {

    private IDifficultyCalculator diffCalc;

    public AionDifficultyRule(IChainCfg<IAionBlock, AionTransaction> configuration) {
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
            A0BlockHeader grandParent,
            A0BlockHeader parent,
            A0BlockHeader current,
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
