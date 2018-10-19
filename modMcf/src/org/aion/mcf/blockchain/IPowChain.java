/*
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
 */
package org.aion.mcf.blockchain;

import java.math.BigInteger;
import org.aion.base.type.Hash256;
import org.aion.base.type.IBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * proof of work chain interface.
 *
 * @param <BLK>
 * @param <BH>
 */
@SuppressWarnings("rawtypes")
public interface IPowChain<BLK extends IBlock, BH extends AbstractBlockHeader>
        extends IGenericChain<BLK, BH> {

    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    BigInteger getTotalDifficultyByHash(Hash256 hash);
}
