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
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.utils;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Prints out thread information.
 */
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
                    sb.append("\tat ").append(ste.getClassName()).append(".").append(ste.getMethodName()).append("(")
                            .append(ste.getFileName()).append(":").append(ste.getLineNumber()).append(")");
                    sb.append("\n");
                }
                sb.append("Ownable synchronizers:");
                LockInfo[] s = threadInfo.getLockedSynchronizers();
                if (s == null || s.length == 0) {
                    sb.append(" None.\n");
                } else {
                    sb.append("\n");
                    for (final LockInfo lockInfo : s) {
                        sb.append(lockInfo.getClassName()).append(" <").append(lockInfo.getIdentityHashCode())
                                .append("> \n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Example of class usage.
     */
    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            System.out.println("Entered Thread 0");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            System.out.println("Exiting Thread 0");
        });
        t1.start();

        Thread t2 = new Thread(() -> {
            System.out.println("Entered Thread 1");
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            System.out.println("Exiting Thread 1");
        });
        t2.start();

        Runnable runnable = () -> {
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