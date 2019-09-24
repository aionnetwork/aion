package org.aion.zero.impl.vm;

import org.junit.Assert;

public final class AvmPathManager {

    public static String getPathOfProjectRootDirectory() {
        String workingDir = System.getProperty("user.dir");
        int indexOfRoot = workingDir.lastIndexOf("modAionImpl");
        Assert.assertTrue(indexOfRoot >= 0);
        return workingDir.substring(0, indexOfRoot);
    }
}
