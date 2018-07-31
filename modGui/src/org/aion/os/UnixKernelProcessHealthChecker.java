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
        if(proc.exitValue() != 0) {
            throw new KernelControlException("Error invoking ps program");
        }

        try (
                final InputStream is = proc.getInputStream();
                final InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
                final BufferedReader br = new BufferedReader(isr);
        ) {
            return br.lines().collect(Collectors.toList());
        } catch (IOException ioe) {
            throw new IOException("Could not get the output of ps program", ioe);
        }
    }
}
