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
package org.aion.base.vm;

/**
 * @author jay
 */
public interface IDataWord {

    /**
     * Returns the byte array data the IDataWord wraps.
     *
     * @return the byte array data the IDataWord wraps.
     */
    byte[] getData();

    /**
     * Returns the underlying byte array, truncated so that leading zero bytes are removed.
     *
     * @return a truncated underlying byte array with all leading zeros removed.
     */
    byte[] getNoLeadZeroesData();

    /**
     * Returns a copy of the IDataWord.
     *
     * @return a cope of the IDataWord.
     */
    IDataWord copy();

    /**
     * Returns true only if the underlying byte array consists only of zero bytes.
     *
     * @return true only if the IDataWord consists of zero bytes.
     * @apiNote An IDataWord whose underlying byte array consists only of zero bytes is interpreted
     * as null by the database.
     */
    boolean isZero();

}
