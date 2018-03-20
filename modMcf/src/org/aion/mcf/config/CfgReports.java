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
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.mcf.config;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Printing reports for debugging purposes.
 *
 * @author Alexandra Roatis
 */
public class CfgReports {

    private boolean enable;
    private String path;
    private int dump_interval;
    private int block_frequency;
    private boolean enable_heap_dumps;
    private int heap_dump_interval;

    public CfgReports() {
        // default configuration
        this.enable = false;
        this.path = "reports";
        this.dump_interval = 10000;
        this.block_frequency = 500;
        this.enable_heap_dumps = false;
        this.heap_dump_interval = 100000;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "enable":
                            this.enable = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "path":
                            this.path = Cfg.readValue(sr);
                            break;
                        case "dump_interval":
                            this.dump_interval = Integer.parseInt(Cfg.readValue(sr));
                            break;
                        case "block_frequency":
                            this.block_frequency = Integer.parseInt(Cfg.readValue(sr));
                            break;
                        case "enable_heap_dumps":
                            this.enable_heap_dumps = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "heap_dump_interval":
                            this.heap_dump_interval = Integer.parseInt(Cfg.readValue(sr));
                            break;
                        default:
                            Cfg.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
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
            xmlWriter.writeStartElement("reports");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable");
            xmlWriter.writeCharacters(String.valueOf(this.isEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("path");
            xmlWriter.writeCharacters(this.getPath());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("dump_interval");
            xmlWriter.writeCharacters(String.valueOf(this.getDumpInterval()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("block_frequency");
            xmlWriter.writeCharacters(String.valueOf(this.getBlockFrequency()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_heap_dumps");
            xmlWriter.writeCharacters(String.valueOf(this.isHeapDumpEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("heap_dump_interval");
            xmlWriter.writeCharacters(String.valueOf(this.getHeapDumpInterval()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeEndElement();
            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            xmlWriter.flush();
            xmlWriter.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean isEnabled() {
        return enable;
    }

    public String getPath() {
        return this.path;
    }

    public int getDumpInterval() {
        return this.dump_interval;
    }

    public int getBlockFrequency() {
        return this.block_frequency;
    }

    public boolean isHeapDumpEnabled() {
        return enable_heap_dumps;
    }

    public int getHeapDumpInterval() {
        return this.heap_dump_interval;
    }

}
