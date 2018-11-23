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
        BufferedWriter out = new BufferedWriter(
            new FileWriter(forkFile, true));
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
