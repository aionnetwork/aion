/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/
package org.aion.mcf.config;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public final class CfgSync {

    private int blocksImportMax;

    private int blocksQueueMax;

    private boolean showStatus;

    public CfgSync() {
        this.blocksImportMax = 192;
        this.blocksQueueMax = 2000;
        this.showStatus = false;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
            case XMLStreamReader.START_ELEMENT:
                String elelmentName = sr.getLocalName().toLowerCase();
                switch (elelmentName) {
                case "blocks-import-max":
                    this.blocksImportMax = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "blocks-queue-max":
                    this.blocksQueueMax = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "show-status":
                    this.showStatus = Boolean.parseBoolean(Cfg.readValue(sr));
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
            xmlWriter.writeStartElement("sync");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-import-max");
            xmlWriter.writeCharacters(this.getBlocksImportMax() + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-queue-max");
            xmlWriter.writeCharacters(this.getBlocksQueueMax() + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("show-status");
            xmlWriter.writeCharacters(this.showStatus + "");
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
            return "";
        }
    }

    public int getBlocksImportMax() {
        return this.blocksImportMax;
    }

    public int getBlocksQueueMax() {
        return this.blocksQueueMax;
    }

    public boolean getShowStatus() {
        return this.showStatus;
    }

}
