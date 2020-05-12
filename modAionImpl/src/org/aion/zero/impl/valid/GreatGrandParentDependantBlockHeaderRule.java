package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.zero.impl.types.BlockHeader;

public interface GreatGrandParentDependantBlockHeaderRule {

    /**
     * A separate class of rules that infer a relationship between the current block, the block
     * preceding (grandParent) and the block preceding that block (greatGrandParent)
     */
    boolean validate(
        BlockHeader grandParent,
        BlockHeader greatGrandParent,
        BlockHeader current,
        List<RuleError> errors);
}
