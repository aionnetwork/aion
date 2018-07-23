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
 *
 ******************************************************************************/
package org.aion.mcf.config;

import com.google.common.base.Objects;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CfgApiZmq {

    CfgApiZmq() {
        this.active = true;
        this.ip = "127.0.0.1";
        this.port = 8547;
        this.filtersEnabled = true;
        this.blockSummaryCacheEnabled = false;
    }

    protected boolean active;
    protected String ip;
    protected int port;
    protected boolean filtersEnabled;
    protected boolean blockSummaryCacheEnabled;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        this.active = Boolean.parseBoolean(sr.getAttributeValue(null, "active"));
        this.ip = sr.getAttributeValue(null, "ip");
        this.port = Integer.parseInt(sr.getAttributeValue(null, "port"));

        // get the nested elements
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "filters-enabled":
                            try {
                                filtersEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                //System.out.println("failed to read config node: aion.api.rpc.filters-enabled; using preset: " + this.filtersEnabled);
                                //e.printStackTrace();
                            }
                            break;
                        case "block-summary-cache":
                            try {
                                blockSummaryCacheEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                //System.out.println("failed to read config node: aion.api.rpc.filters-enabled; using preset: " + this.filtersEnabled);
                                //e.printStackTrace();
                            }
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

        sr.next();
    }

    String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            // <rpc active="false" ip="127.0.0.1" port="8545"/>

            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("java");

            xmlWriter.writeAttribute("active", this.active ? "true" : "false");
            xmlWriter.writeAttribute("ip", this.ip);
            xmlWriter.writeAttribute("port", this.port + "");

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

    public boolean getActive() {
        return this.active;
    }
    public String getIp() {
        return this.ip;
    }
    public int getPort() {
        return this.port;
    }
    public boolean isFiltersEnabled() { return this.filtersEnabled; }
    public boolean isBlockSummaryCacheEnabled() { return this.blockSummaryCacheEnabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgApiZmq cfgApiZmq = (CfgApiZmq) o;
        return active == cfgApiZmq.active &&
                port == cfgApiZmq.port &&
                filtersEnabled == cfgApiZmq.filtersEnabled &&
                blockSummaryCacheEnabled == cfgApiZmq.blockSummaryCacheEnabled &&
                Objects.equal(ip, cfgApiZmq.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(active, ip, port, filtersEnabled, blockSummaryCacheEnabled);
    }
}
