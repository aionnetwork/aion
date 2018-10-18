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
package org.aion.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;

/**
 * @author Alexandra Roatis
 */
public class TestResources {

    private static final String userDir = System.getProperty("user.dir");
    private static final String rawDataFileWithRandomBlocks = "raw-block-data.txt";
    private static final String rawDataFileWithConsecutiveBlocks = "consecutive-raw-block-data.txt";
    private static final String moduleDir = "modAionImpl";
    private static final String testResourceDir = "test_resources";

    public static final File TEST_RESOURCE_DIR =
        userDir.contains(moduleDir)
            ? new File(userDir, testResourceDir)
            : new File(userDir, moduleDir + File.separator + testResourceDir);

    /**
     * Extracts raw block data from a test resource file.
     */
    public static List<byte[]> rawBlockData(String rawDataFile) {
        List<byte[]> parameters = new ArrayList<>();

        File file = new File(TEST_RESOURCE_DIR, rawDataFile);

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String text;

            while ((text = reader.readLine()) != null) {
                byte[] rawData = convertToByteArray(text.split(", "));
                parameters.add(rawData);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
        }

        return parameters;
    }

    /**
     * Extracts raw block data from a test resource file.
     */
    public static List<byte[]> rawBlockData(int limit, String rawDataFile) {
        List<byte[]> parameters = new ArrayList<>();

        File file = new File(TEST_RESOURCE_DIR, rawDataFile);

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String text;
            int count = 0;

            while ((text = reader.readLine()) != null && count < limit) {
                byte[] rawData = convertToByteArray(text.split(", "));
                parameters.add(rawData);
                count++;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
        }

        return parameters;
    }

    private static final byte[] convertToByteArray(String[] numbers) {
        byte[] rawData = new byte[numbers.length];
        for (int i = 0; i < rawData.length; i++) {
            rawData[i] = Byte.parseByte(numbers[i]);
        }
        return rawData;
    }

    /**
     * @return a set of block headers to be used for testing.
     */
    public static List<A0BlockHeader> blockHeaders() {
        List<A0BlockHeader> parameters = new ArrayList<>();

        for (AionBlock block : blocks()) {
            parameters.add(block.getHeader());
        }

        return parameters;
    }

    /**
     * @return a set of blocks to be used for testing.
     */
    public static List<AionBlock> blocks() {
        List<AionBlock> parameters = new ArrayList<>();

        for (byte[] rawData : rawBlockData(rawDataFileWithRandomBlocks)) {
            parameters.add(new AionBlock(rawData));
        }

        return parameters;
    }

    /**
     * @return a set of blocks to be used for testing.
     */
    public static List<AionBlock> blocks(int limit) {
        List<AionBlock> parameters = new ArrayList<>();

        for (byte[] rawData : rawBlockData(limit, rawDataFileWithRandomBlocks)) {
            parameters.add(new AionBlock(rawData));
        }

        return parameters;
    }

    /**
     * @return a set of consecutive blocks (in ascending order of the block number) to be used for
     * testing.
     */
    public static List<AionBlock> consecutiveBlocks(int limit) {
        List<AionBlock> parameters = new ArrayList<>();

        for (byte[] rawData : rawBlockData(limit, rawDataFileWithConsecutiveBlocks)) {
            parameters.add(new AionBlock(rawData));
        }

        return parameters;
    }
}
