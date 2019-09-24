package org.aion.mcf.config;

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;

/** @author chris */
public class CfgLog {

    private Map<LogEnum, LogLevel> modules;
    // TODO: rename to enabled; current name leads to confusion
    boolean logFile;
    String logPath;

    public CfgLog() {
        modules = new HashMap<>();
        modules.put(LogEnum.ROOT, LogLevel.WARN);
        modules.put(LogEnum.CONS, LogLevel.INFO);
        modules.put(LogEnum.CACHE, LogLevel.ERROR);
        modules.put(LogEnum.GEN, LogLevel.INFO);
        modules.put(LogEnum.VM, LogLevel.ERROR);
        modules.put(LogEnum.DB, LogLevel.ERROR);
        modules.put(LogEnum.SYNC, LogLevel.INFO);
        modules.put(LogEnum.API, LogLevel.INFO);
        modules.put(LogEnum.P2P, LogLevel.INFO);
        modules.put(LogEnum.TX, LogLevel.ERROR);
        modules.put(LogEnum.TXPOOL, LogLevel.ERROR);
        modules.put(LogEnum.GUI, LogLevel.INFO);
        modules.put(LogEnum.SURVEY, LogLevel.ERROR);
        this.logFile = false;
        this.logPath = "log";
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        this.modules = new HashMap<>();
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    {
                        /* XML - Takes the input in config.xml and parse as T/F */
                        String elementName = sr.getLocalName().toLowerCase();
                        switch (elementName) {
                            case "log-file":
                                this.logFile = Boolean.parseBoolean(Cfg.readValue(sr));
                                break;
                            case "log-path":
                                this.logPath = Cfg.readValue(sr);
                                break;
                            default:
                                // ensures the LogEnum can be decoded
                                if (LogEnum.contains(elementName)) {
                                    String level = Cfg.readValue(sr);
                                    // ensures the LogLevel can be decoded
                                    if (LogLevel.contains(level)) {
                                        this.modules.put(LogEnum.valueOf(elementName.toUpperCase()), LogLevel.valueOf(level.toUpperCase()));
                                    } else {
                                        // default for incorrect levels
                                        this.modules.put(LogEnum.valueOf(elementName.toUpperCase()), LogLevel.WARN);
                                    }
                                }
                                break;
                        }
                        break;
                    }
                case XMLStreamReader.END_ELEMENT:
                    break loop;
                default:
                    break;
            }
        }
    }

    public String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("log");
            xmlWriter.writeCharacters("\r\n");

            /*
             * XML - Displays tag/entry in the config.xml
             * Boolean value to allow logger to be toggled ON and OFF
             */
            xmlWriter.writeCharacters("\t\t");
            xmlWriter.writeComment(
                    "Enable/Disable logback service; if disabled, output will not be logged.");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("log-file");
            xmlWriter.writeCharacters(this.logFile + "");
            xmlWriter.writeEndElement();
            xmlWriter.writeCharacters("\r\n");

            /*
             * XML - Displays log-path in the config.xml
             * String value to determine the folder path for log files
             */
            xmlWriter.writeCharacters("\t\t");
            xmlWriter.writeComment(
                    "Sets the physical location on disk where log files will be stored.");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("log-path");
            xmlWriter.writeCharacters(this.logPath + "");
            xmlWriter.writeEndElement();
            xmlWriter.writeCharacters("\r\n");

            for (Map.Entry<LogEnum, LogLevel> module : this.modules.entrySet()) {
                xmlWriter.writeCharacters("\t\t");
                xmlWriter.writeStartElement(module.getKey().name());
                xmlWriter.writeCharacters(module.getValue().name());
                xmlWriter.writeEndElement();
                xmlWriter.writeCharacters("\r\n");
            }
            xmlWriter.writeCharacters("\t");
            xmlWriter.writeEndElement();
            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            xmlWriter.flush();
            xmlWriter.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            return "";
        }
    }

    public Map<LogEnum, LogLevel> getModules() {
        return this.modules;
    }

    public boolean updateModule(LogEnum logEnum, LogLevel logLevel) {
        if (modules.containsKey(logEnum) && !modules.get(logEnum).equals(logLevel)) {
            modules.replace(logEnum, logLevel);
            return true;
        } else if (!modules.containsKey(logEnum)) {
            // allows introducing new logs
            modules.put(logEnum, logLevel);
            return true;
        } else {
            return false;
        }
    }

    public void setLogPath(String value) {
        logPath = value;
    }

    /** Method checks whether LOGGER is enabled/disabled */
    public boolean getLogFile() {
        return this.logFile;
    }

    /** Used to turn off logging in case of incorrect configuration. */
    public void disableLogging() {
        this.logFile = false;
    }

    /** Method returns user input folder path of logger */
    public String getLogPath() {
        return logPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgLog cfgLog = (CfgLog) o;
        return logFile == cfgLog.logFile
                && Objects.equal(modules, cfgLog.modules)
                && Objects.equal(logPath, cfgLog.logPath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modules, logFile, logPath);
    }
}
