package org.aion.mcf.core;

import java.math.BigInteger;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * Interface for retrieving difficulty calculations for a particular chain configuration, note that
 * depending on where the corresponding class is generated, it will utilized different algorithms.
 * However the common interface of the current and parent blockHeader will remain.
 *
 * @author yao
 */
@FunctionalInterface
public interface IDifficultyCalculator {
    BigInteger calculateDifficulty(AbstractBlockHeader current, AbstractBlockHeader dependency);
}
