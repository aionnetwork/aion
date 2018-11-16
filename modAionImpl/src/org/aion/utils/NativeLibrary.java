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

package org.aion.utils;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.util.NativeLoader;

public enum NativeLibrary {
    COMMON("common"),
    SODIUM("sodium"),
    EQUIHASH("equihash"),
    ZMQ("zmq"),
    BLAKE2B("blake2b"),
    FASTVM("fastvm"),
    SOLIDITY("solidity");

    private final String name;

    NativeLibrary(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static void checkNativeLibrariesLoaded() {
        List<Exception> exceptionsList = new ArrayList<>();
        for (NativeLibrary lib : NativeLibrary.values()) {
            try {
                NativeLoader.loadLibrary(lib.getName());
            } catch (Exception e) {
                exceptionsList.add(e);
            }
        }

        if (!exceptionsList.isEmpty()) {
            throw new RuntimeException(buildErrorMessage(exceptionsList));
        }
    }

    public static String buildErrorMessage(List<Exception> exceptionList) {
        StringBuilder builder = new StringBuilder();
        builder.append("failed to load native libraries, the following errors were thrown:\n\n");
        for (Exception e : exceptionList) {
            builder.append(e.toString());
            builder.append("\n");
        }
        return builder.toString();
    }
}
