package org.aion.mcf.config;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CfgForkTest {

    private CfgAion cfg;
    private File forkFile;

    @Before
    public void setup() throws IOException {
        new File(System.getProperty("user.dir") + "/mainnet/config").mkdirs();
        forkFile =
                new File(
                        System.getProperty("user.dir")
                                + "/mainnet/config"
                                + CfgFork.FORK_PROPERTIES_PATH);
        forkFile.createNewFile();

        // Open given file in append mode.
        BufferedWriter out = new BufferedWriter(new FileWriter(forkFile, true));
        out.write("fork0.3.2=2000000");
        out.close();

        cfg = CfgAion.inst();
    }

    @After
    public void teardown() {
        forkFile.delete();
        forkFile.getParentFile().delete();
        forkFile.getParentFile().getParentFile().delete();
    }

    @Test
    public void getForkPropertyTest() {

        String forkProperty = cfg.getFork().getProperties().getProperty("fork0.3.2");
        assertEquals("2000000", forkProperty);
    }

    @Test
    public void getForkPropertyTest2() {
        String forkProperty = cfg.getFork().getProperties().getProperty("fork0.3.1");
        assertNull(forkProperty);
    }
}
