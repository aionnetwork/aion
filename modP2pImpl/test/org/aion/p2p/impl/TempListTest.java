/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;

/** @author chris */
public class TempListTest {

    private int cap = 10;

    private Map<Integer, Object> tempList =
            Collections.synchronizedMap(
                    new LinkedHashMap<Integer, Object>() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        protected boolean removeEldestEntry(
                                final Map.Entry<Integer, Object> eldest) {
                            return size() > cap;
                        }
                    });

    @Test
    public void testTempList() {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            executor.submit(
                    () -> {
                        Random r = new Random();
                        for (int i = 0; i < 10000; i++) {
                            int rnd = r.nextInt();
                            tempList.put(rnd, new Object());
                        }
                    });
        }

        try {
            Thread.sleep(5000);

            assertEquals(cap, this.tempList.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
