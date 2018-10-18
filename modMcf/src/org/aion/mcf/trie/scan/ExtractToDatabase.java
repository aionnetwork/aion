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

package org.aion.mcf.trie.scan;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.rlp.Value;

/**
 * @author Alexandra Roatis
 */
public class ExtractToDatabase implements ScanAction {

    public long count = 0;
    // only the keys are relevant so the value will be this constant
    byte[] dummy_value = new byte[]{0};
    IByteArrayKeyValueDatabase db;

    public ExtractToDatabase(IByteArrayKeyValueDatabase _db) {
        this.db = _db;
    }

    @Override
    public void doOnNode(byte[] hash, Value node) {
        db.put(hash, dummy_value);
        count++;
    }
}
