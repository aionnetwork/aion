package org.aion.base.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class MAFTest {
    @Test
    public void testMAFSingleThread() {
        double[] testData = {1, 2, 3, 4, 5, 5, 4, 3, 2, 1};
        int[] windowSizes = {1, 3, 5, 10};

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
        // just try to see if we can get a concurrent modification exception when doing loads of
        // writes
        // and reads to/from MAF

        double[] testData = {1, 2, 3, 4, 5, 5, 4, 3, 2, 1};

        int[] windowSizes = {3, 5};

        final double min = 0, max = 50;
        final int itrCount = 10000;
        MAF ma = new MAF(5);

        Thread producerA =
                new Thread() {
                    public void run() {
                        try {
                            for (int i = 0; i < itrCount; i++) {
                                ma.add(ThreadLocalRandom.current().nextDouble(min, max));
                            }
                            System.out.println("producerA done --------------");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
        Thread producerB =
                new Thread() {
                    public void run() {
                        try {
                            for (int i = 0; i < itrCount; i++) {
                                ma.add(ThreadLocalRandom.current().nextDouble(min, max));
                            }
                            System.out.println("producerB done  --------------");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };

        AtomicBoolean shutdown = new AtomicBoolean(false);
        Thread consumer =
                new Thread() {
                    public void run() {
                        try {
                            while (!shutdown.get()) {
                                System.out.println(ma.getAverage());
                                // Thread.sleep(1);
                            }
                            System.out.println("consumer done --------------");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };

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
