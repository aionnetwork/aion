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
package org.aion.utils;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;

/**
 * Source code form <b>https://blogs.oracle.com/sundararajan/programmatically-dumping-heap-from-java-applications</b>.
 * <p>
 * To analyse the report call <code>jhat filename.hprof</code> and open
 * <b>http://localhost:7000/</b>.
 */
public class HeapDumper {

    // This is the name of the HotSpot Diagnostic MBean
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
    // Field to store the hotspot diagnostic MBean
    private static volatile HotSpotDiagnosticMXBean hotspotMBean;

    /**
     * Call this method from your application whenever you want to dump the heap snapshot into a
     * file.
     *
     * @param fileName name of the heap dump file
     * @param live flag that tells whether to dump only the live objects
     */
    static void dumpHeap(String fileName, boolean live) {
        // initialize hotspot diagnostic MBean
        initHotspotMBean();
        try {
            hotspotMBean.dumpHeap(fileName, live);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    // initialize the hotspot diagnostic MBean field
    private static void initHotspotMBean() {
        if (hotspotMBean == null) {
            synchronized (HeapDumper.class) {
                if (hotspotMBean == null) {
                    hotspotMBean = getHotspotMBean();
                }
            }
        }
    }

    // get the hotspot diagnostic MBean from the
    // platform MBean server
    private static HotSpotDiagnosticMXBean getHotspotMBean() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            HotSpotDiagnosticMXBean bean = ManagementFactory
                .newPlatformMXBeanProxy(server, HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
            return bean;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    /**
     * Example of class usage.
     */
    public static void main(String[] args) {
        // default heap dump file name
        String fileName = "heap.bin.hprof";
        // by default dump only the live objects
        boolean live = true;
        // simple command line options
        switch (args.length) {
            case 2:
                live = args[1].equals("true");
            case 1:
                fileName = args[0];
        }
        // dump the heap
        dumpHeap(fileName, live);
    }
}