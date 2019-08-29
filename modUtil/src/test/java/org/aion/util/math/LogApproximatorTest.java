package org.aion.util.math;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LogApproximatorTest {

    private static double delta = 0.00000000000001;

    @Test
    public void testLog123456789() {
        Assert.assertEquals(Math.log(123456789), LogApproximator.log(new BigInteger("123456789")).toBigDecimal().doubleValue(), delta);
    }

    // Note that this test includes the time for both Math.log() as well as the log approximation
    @Test(timeout = 2000)
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

    // This test calculates the logs for all integers from 1 to 2^20, and records how many of them
    // end up with the same value. That is, it records how many x /= y satisfy log(x) = log(y) using our approximator
    @Test
    public void testCollisions() {
        Set<FixedPoint> logs = new HashSet();
        for (int i = 2; i < (1 << 20); i++) {
//            System.out.println(i);
            FixedPoint log = LogApproximator.log(BigInteger.valueOf(i));
            if (logs.contains(log)) {
                System.out.println("log of " + i + " is " + log + " which is already there");
            } else {
                logs.add(log);
            }
        }
        
    }
}