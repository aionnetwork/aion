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
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Configuration for modGui.  Represents the </code><code>gui</code> section of Aion kernel config.
 */
public class CfgGui {

    private CfgGuiLauncher cfgGuiLauncher;

    /**
     * Constructor.
     */
    public CfgGui() {
        this.cfgGuiLauncher = CfgGuiLauncher.DEFAULT_CONFIG;
    }

    /**
     * Populate this object from XML data
     */
    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elelmentName = sr.getLocalName().toLowerCase();
                    switch (elelmentName) {
                        case "launcher":
                            this.cfgGuiLauncher.fromXML(sr);
                            break;
                        default:
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
            }
        }
    }

    public String toXML() {
        // Hidden for now

        return "";
//        final XMLOutputFactory output = XMLOutputFactory.newInstance();
//        output.setProperty("escapeCharacters", false);
//        XMLStreamWriter xmlWriter;
//        String xml;
//        try {
//            Writer strWriter = new StringWriter();
//            xmlWriter = output.createXMLStreamWriter(strWriter);
//
//            // start element gui
//            xmlWriter.writeCharacters("\r\n\t");
//            xmlWriter.writeStartElement("gui");
//
//            // sub-element launcher
//            xmlWriter.writeCharacters("\r\n\t");
//            xmlWriter.writeCharacters(getCfgGuiLauncher().toXML());
//
//            // close element gui
//            xmlWriter.writeCharacters("\r\n\t");
//            xmlWriter.writeEndElement();
//
//            xml = strWriter.toString();
//            strWriter.flush();
//            strWriter.close();
//            xmlWriter.flush();
//            xmlWriter.close();
//            return xml;
//        } catch (IOException | XMLStreamException e) {
//            e.printStackTrace();
//            return "";
//        }
    }

    public CfgGuiLauncher getCfgGuiLauncher() {
        return cfgGuiLauncher;
    }

    public void setCfgGuiLauncher(CfgGuiLauncher cfgGuiLauncher) {
        this.cfgGuiLauncher = cfgGuiLauncher;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CfgGui cfgGui = (CfgGui) o;
        return Objects.equal(cfgGuiLauncher, cfgGui.cfgGuiLauncher);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cfgGuiLauncher);
    }
}
