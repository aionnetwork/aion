package org.aion.mcf.blockchain.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;

/**
 * Block header rules.
 *
 */
public abstract class BlockHeaderRule extends AbstractValidRule {
    public abstract boolean validate(BlockHeader header, List<RuleError> errors, Object... extraValidationArg);
}
