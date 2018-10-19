/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
package org.aion.mcf.serial;

/**
 * Not currently used yet, will use in future to determine type during deserialization
 *
 * @author yao
 */
public class SerialHeaderValue {
    public static final int BLOCK = 0x0;
    public static final int BLOCKDATA = 0x1;
    public static final int BLOCKHEADER = 0x2;
    public static final int COMMIT = 0x3;
    public static final int PROPOSAL = 0x4;
    public static final int TX = 0x5;
    public static final int PART = 0x6;
    public static final int PARTSETHEADER = 0x7;

    public static String[] serialHeaderString = {
        "block", "blockData", "blockHeader", "commit", "proposal"
    };

    public String getHeaderType(int a) {
        return serialHeaderString[a];
    }
}
