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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public final class CfgNetP2p {

    CfgNetP2p() {
        this.ip = "127.0.0.1";
        this.port = 30303;
        this.discover = false;
        this.showStatus = false;
        this.showLog = false;
        this.bootlistSyncOnly = false;
        this.maxTempNodes = 128;
        this.maxActiveNodes = 128;
        this.errorTolerance = 50;
        this.txBroadcastbuffer = true;
    }

    private String ip;

    private int port;

    private boolean discover;

    private boolean showStatus;

    private boolean showLog;

    private boolean bootlistSyncOnly;

    private int maxTempNodes;

    private int maxActiveNodes;

    private int errorTolerance;

    private boolean txBroadcastbuffer;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
            case XMLStreamReader.START_ELEMENT:
                String elelmentName = sr.getLocalName().toLowerCase();
                switch (elelmentName) {
                case "ip":
                    this.ip = Cfg.readValue(sr);
                    break;
                case "port":
                    this.port = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "discover":
                    this.discover = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "show-status":
                    this.showStatus = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "show-log":
                    this.showLog = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "bootlist-sync-only":
                    this.bootlistSyncOnly = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "max-temp-nodes":
                    this.maxTempNodes = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "max-active-nodes":
                    this.maxActiveNodes = Integer.parseInt(Cfg.readValue(sr));
                    break;
                case "txbroadcastbuffer":
                    this.txBroadcastbuffer = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "err-tolerance":
                    this.errorTolerance = Integer.parseInt(Cfg.readValue(sr));
                    break;
                default:
                    // Cfg.skipElement(sr);
                    break;
                }
                break;
            case XMLStreamReader.END_ELEMENT:
                break loop;
            }
        }
    }

    String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("p2p");

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("ip");
            xmlWriter.writeCharacters(this.getIp());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("port");
            xmlWriter.writeCharacters(this.getPort() + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("discover");
            xmlWriter.writeCharacters(this.discover + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("show-status");
            xmlWriter.writeCharacters(this.showStatus + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("max-temp-nodes");
            xmlWriter.writeCharacters(this.maxTempNodes + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("max-active-nodes");
            xmlWriter.writeCharacters(this.maxActiveNodes + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
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

    public void setIp(final String _ip) {
        this.ip = _ip;
    }

    public void setPort(final int _port) {
        this.port = _port;
    }

    public String getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    public boolean getDiscover() {
        return this.discover;
    }

    public boolean getShowStatus() {
        return this.showStatus;
    }

    public boolean getShowLog() {
        return this.showLog;
    }

    public boolean getBootlistSyncOnly() { return bootlistSyncOnly; }

    public int getMaxTempNodes() {
        return maxTempNodes;
    }

    public int getMaxActiveNodes() {
        return maxActiveNodes;
    }

    public int getErrorTolerance() {
        return errorTolerance;
    }

    public boolean getTxBroadcastbuffer() {
        return txBroadcastbuffer;
    }
}
