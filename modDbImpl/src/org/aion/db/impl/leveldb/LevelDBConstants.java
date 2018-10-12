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

package org.aion.db.impl.leveldb;

/**
 * Constants for LevelDB implementation, used as fallback when nothing else matches
 */
public class LevelDBConstants {

    public static int MAX_OPEN_FILES = 1024;
    public static int BLOCK_SIZE = 4096;
    public static int WRITE_BUFFER_SIZE = 16 * 1024 * 1024;
    public static int CACHE_SIZE = 128 * 1024 * 1024;

    private LevelDBConstants() {
    }
}
