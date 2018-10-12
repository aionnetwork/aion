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

package org.aion.mcf.config;

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * @author chris
 */
public final class CfgSync {

    private static int BLOCKS_QUEUE_MAX = 32;
    private int blocksQueueMax;
    private boolean showStatus;

    public CfgSync() {
        this.blocksQueueMax = BLOCKS_QUEUE_MAX;

        this.showStatus = false;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
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

            // start element sync
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("sync");

            // sub-element blocks-queue-max
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-queue-max");
            xmlWriter.writeCharacters(BLOCKS_QUEUE_MAX + "");
            xmlWriter.writeEndElement();

            // sub-element show-status
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("show-status");
            xmlWriter.writeCharacters(this.showStatus + "");
            xmlWriter.writeEndElement();

            // close element sync
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

    public int getBlocksQueueMax() {
        return this.blocksQueueMax;
    }

    public boolean getShowStatus() {
        return this.showStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CfgSync cfgSync = (CfgSync) o;
        return blocksQueueMax == cfgSync.blocksQueueMax &&
            showStatus == cfgSync.showStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(blocksQueueMax, showStatus);
    }
}
