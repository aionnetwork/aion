package org.aion.os;

import org.aion.mcf.config.CfgGuiLauncher;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;

import static org.junit.Assert.assertThat;

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
        List<String> expectedAionSh = Arrays.asList(
                String.format("%s/script/nohup_wrapper.sh", expectedWorkingDir),
                String.format("%s/aion.sh", expectedWorkingDir)
        );

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
        assertThat(processBuilder.command(), is(Arrays.asList(
                String.format("%s/script/nohup_wrapper.sh", config.getWorkingDir()),
                String.format("%s/%s", config.getWorkingDir(), config.getAionSh())
        )));
    }
}