package org.aion.base.util;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;


public class Bench {
    private static final PrintStream out = System.err;
    private static final int REPORT_INTERVAL = 0;// 100;
    private static final Map<String, Long> timings = new HashMap<String, Long>(50);
    private static final Map<String, Integer> counts = new HashMap<String, Integer>(50);

    public synchronized static void put(String methodName, long ns) {
        Long t = timings.get(methodName);
        Integer c = counts.get(methodName);
        ;
        if (t == null) {
            t = 0L;
            c = 0;
        }
        timings.put(methodName, t + ns);
        counts.put(methodName, ++c);
        if (0 != REPORT_INTERVAL && 0 == c % REPORT_INTERVAL) {
            print();
        }
    }

    public synchronized static void print() {
        out.println("-----timings report-----");
        for (String methodName : timings.keySet()) {
            long t = timings.get(methodName);
            int c = counts.get(methodName);
            out.println("repo." + methodName + ": avg " + (t / c) + " ns");
        }
        out.println("------------------------");
    }
    

    public static void time(String name, Runnable op) {
        long t1 = System.nanoTime();
        try {
            op.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            put(name, System.nanoTime() - t1);
        }
    }
    
    public static <R> R time(String name, Callable<R> op) {
        long t1 = System.nanoTime();
        try {
            return op.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            put(name, System.nanoTime() - t1);
        }
    }
}
