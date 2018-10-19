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

package org.aion.os;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.aion.mcf.config.CfgGuiLauncher;
import org.junit.Test;

/** Test {@link KernelLaunchConfigurator} */
public class KernelLaunchConfiguratorTest {

    @Test
    public void testConfigureAutomatically() throws IOException {
        KernelLaunchConfigurator unit = new KernelLaunchConfigurator();
        ProcessBuilder processBuilder = new ProcessBuilder();

        // unfortunately the method we're testing uses static methods in the System
        // class that we can't really mock/modify safely.  Will just verify that the
        // method uses whatever values are in those System static methods.
        String expectedJavaHome = System.getProperty("java.home");
        String expectedWorkingDir = System.getProperty("user.dir");
        List<String> expectedAionSh =
                Arrays.asList(
                        String.format("%s/script/nohup_wrapper.sh", expectedWorkingDir),
                        String.format("%s/aion.sh", expectedWorkingDir));

        CfgGuiLauncher cfg = new CfgGuiLauncher();
        cfg.setAutodetectJavaRuntime(true);
        unit.configure(cfg, processBuilder);

        assertThat(processBuilder.directory(), is(new File(expectedWorkingDir)));
        assertThat(processBuilder.environment().get("JAVA_HOME"), is(expectedJavaHome));
        assertThat(processBuilder.command(), is(expectedAionSh));
    }

    @Test
    public void testConfigureManually() {
        KernelLaunchConfigurator unit = new KernelLaunchConfigurator();
        ProcessBuilder processBuilder = new ProcessBuilder();
        CfgGuiLauncher config = new CfgGuiLauncher();
        config.setAutodetectJavaRuntime(false);
        config.setAionSh("aionSh");
        config.setWorkingDir("workingDir");
        config.setJavaHome("javaHome");

        unit.configure(config, processBuilder);

        assertThat(processBuilder.directory(), is(new File(config.getWorkingDir())));
        assertThat(processBuilder.environment().get("JAVA_HOME"), is(config.getJavaHome()));
        assertThat(
                processBuilder.command(),
                is(
                        Arrays.asList(
                                String.format("%s/script/nohup_wrapper.sh", config.getWorkingDir()),
                                String.format(
                                        "%s/%s", config.getWorkingDir(), config.getAionSh()))));
    }
}
