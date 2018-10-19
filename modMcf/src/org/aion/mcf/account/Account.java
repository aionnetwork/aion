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
package org.aion.mcf.account;

import org.aion.crypto.ECKey;

/** Account class */
public class Account {

    private ECKey key;
    private long timeout;

    public Account(ECKey k, long t) {
        this.key = k;
        this.timeout = t;
    }

    public void updateTimeout(long t) {
        this.timeout = t;
    }

    public long getTimeout() {
        return this.timeout;
    }

    public ECKey getKey() {

        return this.key;
    }
}
