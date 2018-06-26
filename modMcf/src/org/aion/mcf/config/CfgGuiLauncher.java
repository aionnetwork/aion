package org.aion.mcf.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Configuration for Aion kernel launcher within modGui.
 *
 * Represents the <code>launcher</code> subsection within the </code><code>gui</code> section
 * of Aion kernel config.
 */
public class CfgGuiLauncher {
    private boolean autodetectJavaRuntime;
    private String javaHome;
    private String aionSh;
    private String workingDir;
    private boolean keepKernelOnExit;

    private String kernelPidFile;

    /**
     * Instance of a configuration that uses autodetection.  Provided here for convenience.
     */
    public static final CfgGuiLauncher AUTODETECTING_CONFIG = new CfgGuiLauncher();

    static {
        AUTODETECTING_CONFIG.setAutodetectJavaRuntime(true);
    }

    /** Populate this object from XML data */
    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "autodetect":
                            this.autodetectJavaRuntime = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "java-home":
                            this.javaHome = Cfg.readValue(sr);
                            break;
                        case "aion-sh":
                            this.aionSh = Cfg.readValue(sr);
                        case "working-dir":
                            this.workingDir = Cfg.readValue(sr);
                            break;
                        case "keep-kernel-on-exit":
                            this.keepKernelOnExit = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        default:
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
            }
        }
    }

    /** Serialize object into XML data */


    /** @return working directory */
    public String getWorkingDir() {
        return workingDir;
    }

    /** @param workingDir working directory */
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    /** @return whether kernel launch configuration should be auto-detected */
    public boolean isAutodetectJavaRuntime() {
        return autodetectJavaRuntime;
    }

    /** @param autodetectJavaRuntime whether kernel launch configuration should be auto-detected */
    public void setAutodetectJavaRuntime(boolean autodetectJavaRuntime) {
        this.autodetectJavaRuntime = autodetectJavaRuntime;
    }

    /** @return JAVA_HOME environment variable value */
    public String getJavaHome() {
        return javaHome;
    }

    /** @param javaHome JAVA_HOME environment variable value */
    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    /** @return filename of the script that launches Aion kernel (not full path) */
    public String getAionSh() {
        return aionSh;
    }

    /** @param aionSh filename of the script that launches Aion kernel (not full path) */
    public void setAionSh(String aionSh) {
        this.aionSh = aionSh;
    }

    /** @return whether a launched kernel should keep running after GUI exits */
    public boolean isKeepKernelOnExit() {
        return keepKernelOnExit;
    }

    /** @param keepKernelOnExit whether a launched kernel should keep running after GUI exits */
    public void setKeepKernelOnExit(boolean keepKernelOnExit) {
        this.keepKernelOnExit = keepKernelOnExit;
    }

    /** @return path to kernel pid file */
    public String getKernelPidFile() {
        return kernelPidFile;
    }

    /** @param kernelPidFile path to kernel pid file */
    public void setKernelPidFile(String kernelPidFile) {
        this.kernelPidFile = kernelPidFile;
    }
}
