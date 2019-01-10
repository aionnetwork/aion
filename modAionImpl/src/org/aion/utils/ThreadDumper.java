package org.aion.utils;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/** Prints out thread information. */
public class ThreadDumper {
    public static String dumpThreadInfo() {
        final StringBuilder sb = new StringBuilder();
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive()) {
                ThreadInfo threadInfo = threadMXBean.getThreadInfo(t.getId());
                sb.append(threadInfo.toString());
                sb.deleteCharAt(sb.length() - 1);
                for (StackTraceElement ste : t.getStackTrace()) {
                    sb.append("\tat ")
                            .append(ste.getClassName())
                            .append(".")
                            .append(ste.getMethodName())
                            .append("(")
                            .append(ste.getFileName())
                            .append(":")
                            .append(ste.getLineNumber())
                            .append(")");
                    sb.append("\n");
                }
                sb.append("Ownable synchronizers:");
                LockInfo[] s = threadInfo.getLockedSynchronizers();
                if (s == null || s.length == 0) {
                    sb.append(" None.\n");
                } else {
                    sb.append("\n");
                    for (final LockInfo lockInfo : s) {
                        sb.append(lockInfo.getClassName())
                                .append(" <")
                                .append(lockInfo.getIdentityHashCode())
                                .append("> \n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** Example of class usage. */
    public static void main(String[] args) throws InterruptedException {
        Thread t1 =
                new Thread(
                        () -> {
                            System.out.println("Entered Thread 0");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                throw new IllegalStateException(e);
                            }
                            System.out.println("Exiting Thread 0");
                        });
        t1.start();

        Thread t2 =
                new Thread(
                        () -> {
                            System.out.println("Entered Thread 1");
                            try {
                                Thread.sleep(4000);
                            } catch (InterruptedException e) {
                                throw new IllegalStateException(e);
                            }
                            System.out.println("Exiting Thread 1");
                        });
        t2.start();

        Runnable runnable =
                () -> {
                    System.out.println("Entered Thread 2");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    System.out.println("Exiting Thread 2");
                };
        Thread t3 = new Thread(runnable);
        t3.start();

        // print the threads
        System.out.println(dumpThreadInfo());
        Thread.sleep(3000);
        System.out.println(dumpThreadInfo());
        Thread.sleep(1000);
        System.out.println(dumpThreadInfo());
    }
}
