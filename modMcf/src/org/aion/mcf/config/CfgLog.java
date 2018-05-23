/*******************************************************************************
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
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/
package org.aion.mcf.config;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.log.LogEnum;
import org.aion.log.LogLevels;

/** @author chris */
public class CfgLog {

    private Map<String, String> modules;
    private boolean logFile;
    private String logPath;
    private boolean validInput;

    public CfgLog() {
        modules = new HashMap<>();
        modules.put(LogEnum.CONS.name(), LogLevels.INFO.name());
        modules.put(LogEnum.GEN.name(), LogLevels.INFO.name());
        modules.put(LogEnum.VM.name(), LogLevels.ERROR.name());
        modules.put(LogEnum.DB.name(), LogLevels.ERROR.name());
        modules.put(LogEnum.SYNC.name(), LogLevels.INFO.name());
        modules.put(LogEnum.API.name(), LogLevels.INFO.name());
        modules.put(LogEnum.TX.name(), LogLevels.ERROR.name());
        modules.put(LogEnum.TXPOOL.name(), LogLevels.ERROR.name());
        this.logFile = false;
        this.logPath = "log";
        this.validInput = true;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        this.modules = new HashMap<>();
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:

                    /** XML - Takes the input in config.xml and parse as T/F */
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "log-file":
                            this.logFile = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "log-path":
                            this.logPath = Cfg.readValue(sr);
                            if (!isValidPath()) {
                                this.validInput = false;
                            }
                            break;
                        default:
                            break;
                    }

                    elementName = sr.getLocalName().toUpperCase();
                    if (LogEnum.contains(elementName))
                        this.modules.put(elementName, Cfg.readValue(sr).toUpperCase());
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
                default:
                    // Cfg.skipElement(sr);
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

            /** XML - Displays tag/entry in the config.xml */
            xmlWriter.writeCharacters("\t\t");
            xmlWriter.writeStartElement("log-file");
            xmlWriter.writeCharacters(this.logFile + "");
            xmlWriter.writeEndElement();
            xmlWriter.writeCharacters("\r\n");

            /** XML - Displays log-path in the config.xml */
            xmlWriter.writeCharacters("\t\t");
            xmlWriter.writeStartElement("log-path");
            xmlWriter.writeCharacters(this.logPath + "");
            xmlWriter.writeEndElement();
            xmlWriter.writeCharacters("\r\n");

            for (Map.Entry<String, String> module : this.modules.entrySet()) {
                xmlWriter.writeCharacters("\t\t");
                xmlWriter.writeStartElement(module.getKey().toUpperCase());
                xmlWriter.writeCharacters(module.getValue().toUpperCase());
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

    public Map<String, String> getModules() {
        return this.modules;
    }

    /** Method checks value of logFile as T/F */
    public boolean getLogFile() {
        return this.logFile;
    }

    /** Method returns user input path of logFile */
    public String getLogPath() {
        if (!isValidPath()) {
            return this.logPath = "log";
        } else {
            return this.logPath;
        }
    }

    /** Method checks logPath for illegal inputs */
    public boolean isValidPath() {
        return !logPath.matches(".*[-=+,.?;:'!@#$%^&*].*");
    }

    /** Method returns logPath input validity */
    public boolean isValidInput() {
        return this.validInput;
    }
}
