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

import java.io.File;
import java.util.Map;
import org.aion.mcf.config.CfgGuiLauncher;

/** Sets up configuration for launching kernel in a separate OS process. */
public class KernelLaunchConfigurator {
    private static final String NOHUP_WRAPPER = "script/nohup_wrapper.sh";
    private static final String DEFAULT_AION_SH = "aion.sh";

    /**
     * Set parameters on a {@link ProcessBuilder} to configure it so it is ready to launch kernel.
     *
     * <p>Parameters on the ProcessBuilder that clash with the parameters that this method is trying
     * to set will be overwritten, but others will be left alone.
     *
     * @param config configuration
     * @param processBuilder object in which parameters will be applied
     */
    public void configure(CfgGuiLauncher config, ProcessBuilder processBuilder) {
        if (config.isAutodetectJavaRuntime()) {
            configureAutomatically(processBuilder);
        } else {
            configureManually(config, processBuilder);
        }

        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    }

    private void configureAutomatically(ProcessBuilder processBuilder) {
        CfgGuiLauncher config = new CfgGuiLauncher();
        String javaHome = System.getProperty("java.home");
        String workingDir = System.getProperty("user.dir");

        config.setJavaHome(javaHome);
        config.setWorkingDir(workingDir);
        config.setAionSh(DEFAULT_AION_SH); // will this blow up on Windows?
        configureManually(config, processBuilder);
    }

    private void configureManually(CfgGuiLauncher config, ProcessBuilder processBuilder) {
        Map<String, String> envVars = processBuilder.environment();
        envVars.put("JAVA_HOME", config.getJavaHome());
        processBuilder.directory(new File(config.getWorkingDir()));

        // invoke the actual command from nohup; otherwise, if a user sends ctrl-C
        // to the GUI program, the spawned process will also be killed
        processBuilder.command(
                String.format("%s/%s", config.getWorkingDir(), NOHUP_WRAPPER),
                String.format("%s/%s", config.getWorkingDir(), config.getAionSh()));
    }
}
