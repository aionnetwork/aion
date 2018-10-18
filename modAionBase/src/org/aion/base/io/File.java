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

package org.aion.base.io;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class File {
    public static List<java.io.File> getFiles(final Path path) {
        if (path == null) {
            System.out.println("getFiles null path input!");
            return Collections.emptyList();
        }

        try {
            java.io.File[] files = path.toFile().listFiles();
            return files != null ? Arrays.asList(files) : Collections.emptyList();
        } catch (UnsupportedOperationException | NullPointerException e) {
            System.out.println("getFiles exception: " + e.toString());
            return Collections.emptyList();
        }
    }
}
