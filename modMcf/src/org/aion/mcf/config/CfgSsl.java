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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class CfgSsl {
    public static final String SSL_KEYSTORE_DIR = "./sslKeystore";
    private static final String ENABLED_TAG = "enabled";
    private static final String CERTIFICATE_TAG = "certificate";
    private static final String PASSWORD_TAG = "password";
    private boolean enabled;
    private String cert;
    private String pass;

    CfgSsl() {
        this.enabled = false;
        this.cert = "identity.jks";
        this.pass = "password";
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case ENABLED_TAG:
                            try {
                                this.enabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println("failed to read config node: " +
                                    "aion.api.rpc.ssl.enabled; using preset: " + this.enabled);
                                e.printStackTrace();
                            }
                            break;
                        case CERTIFICATE_TAG:
                            try {
                                this.cert = String.valueOf(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println("failed to read config node: " +
                                    "aion.api.rpc.ssl.enabled; using preset: " + this.enabled);
                                e.printStackTrace();
                            }
                            break;
                        case PASSWORD_TAG:
                            try {
                                this.pass = String.valueOf(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println("failed to read config node: " +
                                    "aion.api.rpc.ssl.password; using preset: " + this.pass);
                                e.printStackTrace();
                            }
                            break;
                        default:
                            Cfg.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    break loop;
            }
        }
        //sr.next();
    }

    String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("ssl");

            xmlWriter.writeCharacters("\r\n\t\t\t\t");
            xmlWriter.writeComment("toggle ssl on-off " +
                "(if on you cannot access json-rpc over plain http)");
            xmlWriter.writeCharacters("\r\n\t\t\t\t");
            xmlWriter.writeStartElement(ENABLED_TAG);
            xmlWriter.writeCharacters(this.enabled ? "true" : "false");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t\t");
            xmlWriter.writeComment("file name of certificate to use (eg. identity.jks)");
            xmlWriter.writeCharacters("\r\n\t\t\t\t");
            xmlWriter.writeStartElement(CERTIFICATE_TAG);
            xmlWriter.writeCharacters(this.cert);
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t\t");
            xmlWriter.writeComment("password for ssl certificate");
            xmlWriter.writeCharacters("\r\n\t\t\t\t");
            xmlWriter.writeStartElement(PASSWORD_TAG);
            xmlWriter.writeCharacters(this.pass);
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
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

    public boolean getEnabled() { return this.enabled; }
    public String getCert() { return this.cert; }
    public String getPass() { return this.pass; }
}
