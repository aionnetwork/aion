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
import java.util.ArrayList;
import java.util.List;

/**
 * @author chris
 */
public final class CfgNet {

    private static final boolean SINGLE = false;

    private int id;

    public CfgNet() {
        this.id = 2;
        this.nodes = new String[0];
        this.p2p = new CfgNetP2p();
    }

    protected String[] nodes;

    protected CfgNetP2p p2p;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
            case XMLStreamReader.START_ELEMENT:
                String elementName = sr.getLocalName().toLowerCase();
                switch (elementName) {
                    case "id":
                        int _id = Integer.parseInt(Cfg.readValue(sr));
                        this.id = _id < 0 ? 0 : _id;
                        break;
                    case "nodes":
                        List<String> nodes = new ArrayList<>();
                        loopNode:
                        while (sr.hasNext()) {
                            int eventType1 = sr.next();
                            switch (eventType1) {
                            case XMLStreamReader.START_ELEMENT:
                                nodes.add(Cfg.readValue(sr));
                                break;
                            case XMLStreamReader.END_ELEMENT:
                                this.nodes = nodes.toArray(new String[nodes.size()]);
                                break loopNode;
                            }
                        }
                        break;
                    case "p2p":
                        this.p2p.fromXML(sr);
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
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);

            // start element net
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("net");

            // sub-element id
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("id");
            xmlWriter.writeCharacters(this.id + "");
            xmlWriter.writeEndElement();

            // sub-element nodes
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("nodes");
            for (String node : nodes) {
                xmlWriter.writeCharacters("\r\n\t\t\t");
                xmlWriter.writeStartElement("node");
                xmlWriter.writeCharacters(node);
                xmlWriter.writeEndElement();
            }
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeEndElement();

            // sub-element p2p
            xmlWriter.writeCharacters(this.p2p.toXML());

            // close element net
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

    public void setNodes(String[] _nodes) {
        if (SINGLE)
            this.nodes = new String[0];
        else
            this.nodes = _nodes;
    }

    public int getId(){
        return this.id;
    }

    public String[] getNodes() {
        if (SINGLE)
            return new String[0];
        else
            return this.nodes;
    }

    public CfgNetP2p getP2p() {
        return this.p2p;
    }

}
