/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

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
public final class CfgSync {

    private int blocksBackwardMin;
    private int blocksBackwardMax;

    private int blocksRequestMax;
    private int blocksResponseMax;

    private int blocksQueueMax;

    private boolean showStatus;

    private static int BLOCKS_BACKWARD_MIN = 8;
    private static int BLOCKS_BACKWARD_MAX = 64;
    private static int BLOCKS_REQUEST_MAX = 96;
    private static int BLOCKS_RESPONSE_MAX = 96;
    private static int BLOCKS_QUEUE_MAX = 48;

    public CfgSync() {
        this.blocksBackwardMin = BLOCKS_BACKWARD_MIN;
        this.blocksBackwardMax = BLOCKS_BACKWARD_MAX;

        this.blocksRequestMax = BLOCKS_REQUEST_MAX;
        this.blocksResponseMax = BLOCKS_RESPONSE_MAX;

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
                case "blocks-backward-min":
                    this.blocksBackwardMin = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "blocks-backward-max":
                    this.blocksBackwardMax = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "blocks-request-max":
                    this.blocksRequestMax = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "blocks-response-max":
                    this.blocksResponseMax = Integer.parseInt(Cfg.readValue(sr));
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

            // start element sync
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("sync");

            // sub-element blocks-backward-min
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-backward-min");
            xmlWriter.writeCharacters(BLOCKS_BACKWARD_MIN + "");
            xmlWriter.writeEndElement();

            // sub-element blocks-backward-max
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-backward-max");
            xmlWriter.writeCharacters(BLOCKS_BACKWARD_MAX + "");
            xmlWriter.writeEndElement();

            // sub-element blocks-request-max
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-request-max");
            xmlWriter.writeCharacters(BLOCKS_REQUEST_MAX + "");
            xmlWriter.writeEndElement();

            // sub-element blocks-response-max
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-response-max");
            xmlWriter.writeCharacters(BLOCKS_RESPONSE_MAX + "");
            xmlWriter.writeEndElement();

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

    public int getBlocksBackwardMin() {
        return this.blocksBackwardMin;
    }

    public int getBlocksBackwardMax() {
        return this.blocksBackwardMax;
    }

    public int getBlocksRequestMax() {
        return this.blocksRequestMax;
    }

    public int getBlocksResponseMax() {
        return this.blocksResponseMax;
    }

    public int getBlocksQueueMax() {
        return this.blocksQueueMax;
    }

    public boolean getShowStatus() {
        return this.showStatus;
    }

}
