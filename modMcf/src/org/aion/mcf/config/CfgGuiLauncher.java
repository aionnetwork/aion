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
//    private boolean keepKernelOnExit;

    private String kernelPidFile;

    /**
     * Instance of a configuration that uses autodetection.  Provided here for convenience.
     */
    public static final CfgGuiLauncher DEFAULT_CONFIG = new CfgGuiLauncher();

    static {
        DEFAULT_CONFIG.setAutodetectJavaRuntime(true);
        // below parameters have no effect since auto-detect is on, but we'll fill them
        // out so that they show up if #toXML is called
        DEFAULT_CONFIG.setJavaHome("/placeholder/for/java_home");
        DEFAULT_CONFIG.setJavaHome("aion.sh");
        DEFAULT_CONFIG.setWorkingDir("/placeholder/for/aion_root_dir");
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
//                        case "keep-kernel-on-exit":
//                            this.keepKernelOnExit = Boolean.parseBoolean(Cfg.readValue(sr));
//                            break;
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

//    /** @return whether a launched kernel should keep running after GUI exits */
//    public boolean isKeepKernelOnExit() {
//        return keepKernelOnExit;
//    }
//
//    /** @param keepKernelOnExit whether a launched kernel should keep running after GUI exits */
//    public void setKeepKernelOnExit(boolean keepKernelOnExit) {
//        this.keepKernelOnExit = keepKernelOnExit;
//    }

    /** @return path to kernel pid file */
    public String getKernelPidFile() {
        return kernelPidFile;
    }

    /** @param kernelPidFile path to kernel pid file */
    public void setKernelPidFile(String kernelPidFile) {
        this.kernelPidFile = kernelPidFile;
    }

    public String toXML() {
        // Hidden for now
        return "";

//        final XMLOutputFactory output = XMLOutputFactory.newInstance();
//        output.setProperty("escapeCharacters", false);
//        XMLStreamWriter xmlWriter;
//        String xml;
//        try {
//            Writer strWriter = new StringWriter();
//            xmlWriter = output.createXMLStreamWriter(strWriter);
//
//            // start element gui
//            xmlWriter.writeCharacters("\t");
//            xmlWriter.writeStartElement("launcher");
//
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeComment("Whether JVM settings for launching kernel should be autodetected; " +
//                    "'true' or 'false'");
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeStartElement("autodetect");
//            xmlWriter.writeCharacters(String.valueOf(isAutodetectJavaRuntime()));
//            xmlWriter.writeEndElement();
//
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeComment("Path to JAVA_HOME.  This field has no effect if autodetect is true.");
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeStartElement("java-home");
//            xmlWriter.writeCharacters(getJavaHome());
//            xmlWriter.writeEndElement();
//
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeComment("Working directory of kernel process.  This field has no effect if autodetect is true.");
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeStartElement("working-dir");
//            xmlWriter.writeCharacters(getWorkingDir());
//            xmlWriter.writeEndElement();
//
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeComment("Filename of aion launcher script, relative to working-dir.  This field has no effect if autodetect is true.");
//            xmlWriter.writeCharacters("\r\n\t\t\t");
//            xmlWriter.writeStartElement("aion-sh");
//            xmlWriter.writeCharacters(getAionSh());
//            xmlWriter.writeEndElement();
//
//            // close element gui
//            xmlWriter.writeCharacters("\r\n\t\t");
//            xmlWriter.writeEndElement();
//
//            xml = strWriter.toString();
//            strWriter.flush();
//            strWriter.close();
//            xmlWriter.flush();
//            xmlWriter.close();
//            return xml;
//        } catch (IOException | XMLStreamException e) {
//            e.printStackTrace();
//            return "";
//        }
    }
}
