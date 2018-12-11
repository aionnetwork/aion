package org.aion.mcf.blockchain.valid;

import java.util.List;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * Block header rules.
 *
 * @param <BH>
 */
public abstract class BlockHeaderRule<BH extends AbstractBlockHeader> extends AbstractValidRule {
    public abstract boolean validate(BH header, List<RuleError> errors);
}
