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
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 * <ether.camp> team through the ethereumJ library. Ether.Camp Inc. (US) team through Ethereum
 * Harmony. John Tromp through the Equihash solver. Samuel Neves through the BLAKE2 implementation.
 * Zcash project team. Bitcoinj team.
 * ****************************************************************************
 */
package org.aion.db.impl;

import org.aion.base.db.IByteArrayKeyValueDatabase;

public interface IDriver {

    /**
     * Connect to a database. You need to call open() afterward before db operations.
     *
     * @param info the parameters for this database, all represented in String.
     * @return HashMapDB, or null.
     */
    IByteArrayKeyValueDatabase connect(java.util.Properties info);

    /**
     * Retrieves the driver's major version number. Initially this should be 1.
     *
     * @return driver's major version number
     */
    int getMajorVersion();

    /**
     * Gets the driver's minor version number. Initially this should be 0.
     *
     * @return driver's minor version number
     */
    int getMinorVersion();
}
