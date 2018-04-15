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
package org.aion.equihash;

import org.aion.base.type.ISolution;
import org.aion.zero.types.IAionBlock;

/**
 * This class encapsulates a valid solution for the given block. This class
 * allows solutions to be passed between classes as needed.
 *
 * @author Ross Kitsis (ross@nuco.io)
 *
 */

public class Solution implements ISolution {

    private IAionBlock block;
    private byte[] nonce;
    private byte[] solution;

    public Solution(IAionBlock block, byte[] nonce, byte[] solution) {

        this.block = block;
        this.nonce = nonce;
        this.solution = solution;
    }

    public IAionBlock getBlock() {
        return block;
    }

    public void setBlock(IAionBlock block) {
        this.block = block;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public byte[] getSolution() {
        return solution;
    }

    public void setSolution(byte[] solution) {
        this.solution = solution;
    }
}
