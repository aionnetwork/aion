package org.aion.utils;

import org.aion.base.util.NativeLoader;

import java.util.ArrayList;
import java.util.List;

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