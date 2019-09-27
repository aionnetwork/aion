package org.aion.vm.avm.resources;

import org.junit.Assert;

public final class PathManager {

    /**
     * Returns the project root directory. This assumes that the tests are being run from a directory
     * named modAvmProvider inside the aion directory!
     */
    public static String fetchProjectRootDir() {
        String workingDir = System.getProperty("user.dir");
        int indexOfRoot = workingDir.lastIndexOf("modVM");
        Assert.assertTrue(indexOfRoot >= 0);
        return workingDir.substring(0, indexOfRoot);
    }
}
