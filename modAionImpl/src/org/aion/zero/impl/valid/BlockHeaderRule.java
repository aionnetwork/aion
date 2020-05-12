package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.zero.impl.types.BlockHeader;

/**
 * Block header rules.
 *
 */
public interface BlockHeaderRule {
    boolean validate(BlockHeader header, List<RuleError> errors);
}
