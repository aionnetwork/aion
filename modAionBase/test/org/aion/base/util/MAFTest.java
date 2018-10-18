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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/

package org.aion.base.util;
import org.junit.Test;
import java.util.concurrent.ThreadLocalRandom;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

public class MAFTest {
    @Test
    public void testMAFSingleThread() {
        double[] testData = {1,2,3,4,5,5,4,3,2,1};
        int[] windowSizes = {1,3,5,10};

        for (int windSize : windowSizes) {
            System.out.println("Window size = " + windSize);
            MAF ma = new MAF(windSize);
            System.out.println("Initial SMA = " + ma.getAverage());
            for (double x : testData) {
                ma.add(x);
                System.out.println("Current number = " + x + ", SMA = " + ma.getAverage());
            }
            System.out.println();
        }
    }

    @Test
    public void testMAFMultiThread() {
        // just try to see if we can get a concurrent modification exception when doing loads of writes
        // and reads to/from MAF

        double[] testData = {1,2,3,4,5,5,4,3,2,1};

        int[] windowSizes = {3,5};

        final double min=0, max=50;
        final int itrCount = 10000;
        MAF ma = new MAF(5);

        Thread producerA = new Thread() { public void run() {
            try {
                for (int i=0; i<itrCount; i++) {
                    ma.add(ThreadLocalRandom.current().nextDouble(min, max));
                }
                System.out.println("producerA done --------------");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }};
        Thread producerB = new Thread() { public void run() {
            try {
                for (int i = 0; i < itrCount; i++) {
                    ma.add(ThreadLocalRandom.current().nextDouble(min, max));
                }
                System.out.println("producerB done  --------------");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }};

        AtomicBoolean shutdown = new AtomicBoolean(false);
        Thread consumer = new Thread() { public void run() {
            try {
                while (!shutdown.get()) {
                    System.out.println(ma.getAverage());
                    //Thread.sleep(1);
                }
                System.out.println("consumer done --------------");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }};

        try {
            producerA.start();
            producerB.start();
            consumer.start();

            producerA.join();
            producerB.join();
            shutdown.set(true);
            consumer.join();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(ma.getAverage());
        System.out.println(ma.getAverage());
    }




}
