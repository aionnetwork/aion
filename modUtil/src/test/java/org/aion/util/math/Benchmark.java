package org.aion.util.math;

import java.math.BigInteger;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

public class Benchmark {

    private static double delta = 0.00000000000001;
    // Note that this test includes the time for both Math.log() as well as the log approximation
    @Test(timeout = 5000)
    public void test100000RandomLogs() {

        Random rng = new Random();
        long seed = rng.nextLong();
        System.out.println("Random test's seed is " + seed);
        rng.setSeed(seed);

        for (int i = 0; i < 100000; i++) {
            long value = Math.abs(rng.nextLong());
            if (value == 0) {
                value++;
            }
            Assert.assertEquals(Math.log(value), LogApproximator.log(BigInteger.valueOf(value)).toBigDecimal().doubleValue(), delta);
        }

    }
}
