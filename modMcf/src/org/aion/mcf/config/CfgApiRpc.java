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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author chris lin, ali sharif
 */
public final class CfgApiRpc {

    CfgApiRpc() {
        this.active = true;
        this.ip = "127.0.0.1";
        this.port = 8545;
        this.enabled = new ArrayList<>(Arrays.asList("web3", "eth", "personal", "stratum"));
        this.corsEnabled = false;
        this.maxthread = 1;
        this.filtersEnabled = true;
    }

    private boolean active;
    private String ip;
    private int port;
    private List<String> enabled;
    private boolean corsEnabled;
    private int maxthread;
    private boolean filtersEnabled;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        // get the attributes
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
                        case "cors-enabled":
                            try {
                                corsEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println("failed to read config node: aion.api.rpc.cors-enabled; using preset: " + corsEnabled);
                                //e.printStackTrace();
                            }
                            break;
                        case "apis-enabled":
                            String cs = Cfg.readValue(sr).trim();
                            this.enabled = new ArrayList<>(
                                    Stream.of(cs.split(","))
                                    .map(String::trim)
                                    .collect(Collectors.toList())
                            );
                            break;
                        case "threads":
                            int t = 0;
                            try {
                                t = Integer.parseInt(Cfg.readValue(sr));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            // filter out negative thread counts
                            if (t > 0)
                                this.maxthread = t;
                            break;
                        case "filters-enabled":
                            try {
                                filtersEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println("failed to read config node: aion.api.rpc.filters-enabled; using preset: " + this.filtersEnabled);
                                e.printStackTrace();
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
            xmlWriter.writeStartElement("rpc");

            xmlWriter.writeAttribute("active", this.active ? "true" : "false");
            xmlWriter.writeAttribute("ip", this.ip);
            xmlWriter.writeAttribute("port", this.port + "");

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment("boolean, enable/disable cross origin requests (browser enforced)");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("cors-enabled");
            xmlWriter.writeCharacters(String.valueOf(this.getCorsEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment("comma-separated list, APIs available: web3,net,debug,personal,eth,stratum");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("apis-enabled");
            xmlWriter.writeCharacters(String.join(",", this.getEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment("size of thread pool allocated for rpc requests");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("threads");
            xmlWriter.writeCharacters(this.maxthread + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment("enable web3 filters. some web3 clients depend on this and wont work as expected if turned off");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("filters-enabled");
            xmlWriter.writeCharacters(String.valueOf(this.filtersEnabled));
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
    public boolean getCorsEnabled() {
        return corsEnabled;
    }
    public List<String> getEnabled() {
        return enabled;
    }
    public int getMaxthread() { return maxthread; }
    public boolean isFiltersEnabled() {
        return filtersEnabled;
    }
}