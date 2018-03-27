/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.valid;

import static org.aion.base.util.BIUtil.isEqual;

import java.math.BigInteger;
import java.util.List;

import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.mcf.valid.DependentBlockHeaderRule;

/**
 * Checks block's difficulty against calculated difficulty value
 */
public class AionDifficultyRule extends DependentBlockHeaderRule<A0BlockHeader> {

    IDifficultyCalculator diffCalc;

    public AionDifficultyRule(IChainCfg<AionBlock, AionTransaction> configuration) {
        this.diffCalc = configuration.getDifficultyCalculator();
    }

    @Override
    public boolean validate(A0BlockHeader header, A0BlockHeader parent, List<RuleError> errors) {
        BigInteger calcDifficulty = this.diffCalc.calculateDifficulty(header, parent);
        BigInteger difficulty = header.getDifficultyBI();

        if (!isEqual(difficulty, calcDifficulty)) {
            addError(formatError(calcDifficulty, difficulty), errors);
            return false;
        }
        return true;
    }

    private static String formatError(BigInteger expectedDifficulty, BigInteger actualDifficulty) {
        return "difficulty ("
                + actualDifficulty
                + ") != expected difficulty ("
                + expectedDifficulty + ")";
    }
}
