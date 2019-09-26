package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;

public interface GreatGrandParentDependantBlockHeaderRule {

    /**
     * A separate class of rules that infer a relationship between the current block, the block
     * preceding (grandParent) and the block preceding that block (greatGrandParent)
     */
    boolean validate(
        BlockHeader greatGrandParent,
        BlockHeader grandParent,
        BlockHeader current,
        List<RuleError> errors);
}
