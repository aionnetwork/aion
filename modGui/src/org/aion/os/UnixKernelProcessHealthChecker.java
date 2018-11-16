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

import com.google.common.base.Charsets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class UnixKernelProcessHealthChecker {
    private String AION_KERNEL_SCRIPT = "aion.sh";

    public boolean checkIfKernelRunning(long pid) throws KernelControlException {
        try {
            List<String> psOutput = callPs(pid);
            return !psOutput.isEmpty() && psOutput.get(0).endsWith(AION_KERNEL_SCRIPT);
        } catch (IOException | InterruptedException ex) {
            throw new KernelControlException("Failed to determine kernel process state.", ex);
        }
    }

    private List<String> callPs(long pid)
            throws IOException, InterruptedException, KernelControlException {
        final String[] command = new String[] {"ps", "-o", "comm=", "--pid", String.valueOf(pid)};
        Process proc = new ProcessBuilder().command(command).start();
        proc.waitFor();

        try (final InputStream is = proc.getInputStream();
                final InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
                final BufferedReader br = new BufferedReader(isr); ) {
            return br.lines().collect(Collectors.toList());
        } catch (IOException ioe) {
            throw new IOException("Could not get the output of ps program", ioe);
        }
    }
}
