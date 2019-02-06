package org.zeromq;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Simple App to display version information about jzmq.
 * 
 */
public class App {
    public static void main(final String[] args) throws Exception {
        final Package p = App.class.getPackage();
        final String appname = p.getSpecificationTitle();
        final String versionMaven = p.getSpecificationVersion();
        String[] version = new String[] { "", "" };
        if (p.getImplementationVersion() != null) {
            version = p.getImplementationVersion().split(" ", 2);
        }

        String zmqVersion = null;

        try {

            final int major = ZMQ.version_major();
            final int minor = ZMQ.version_minor();
            final int patch = ZMQ.version_patch();
            zmqVersion = major + "." + minor + "." + patch;

        } catch (Throwable x) {
            zmqVersion = "ERROR! " + x.getMessage();
        }

        final String fmt = "%-7.7s %-15.15s %s%n";

        System.out.printf(fmt, "ZeroMQ", "version:", zmqVersion);
        System.out.printf(fmt, appname, "version:", versionMaven);
        System.out.printf(fmt, appname, "build time:", version[1]);
        System.out.printf(fmt, appname, "build commit:", version[0]);
    }
}