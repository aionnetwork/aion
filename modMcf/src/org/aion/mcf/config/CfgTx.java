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
 * @author chris
 */
public class CfgTx {

    public CfgTx() {
        this.cacheMax = 256;   // by 0.1M;
        this.buffer = true;
        this.poolDump = false;
    }

    private int cacheMax;

    private boolean buffer;

    private boolean poolDump;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
            case XMLStreamReader.START_ELEMENT:
                String elementName = sr.getLocalName().toLowerCase();
                switch (elementName) {
                case "cachemax":
                    this.cacheMax = Integer.parseInt(Cfg.readValue(sr));
                    if (this.cacheMax < 128) {
                        this.cacheMax = 128;
                    } else if (this.cacheMax > 16384) { // 16GB
                        this.cacheMax = 16384;
                    }
                    break;
                case "buffer":
                    this.buffer = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "pooldump":
                    this.poolDump = Boolean.parseBoolean(Cfg.readValue(sr));
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
            xmlWriter.writeStartElement("tx");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("cacheMax");
            xmlWriter.writeCharacters(String.valueOf(this.getCacheMax()));
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

    public int getCacheMax() {
        return this.cacheMax;
    }

    public boolean getBuffer() {
        return this.buffer;
    }

    public boolean getPoolDump() {
        return poolDump;
    }
}






















