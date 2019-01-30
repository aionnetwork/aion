package org.aion.mcf.config;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** Configuration section for VM */
public class CfgVm {
    private boolean avmEnabled;

    /** construct VM configuration with default values */
    public CfgVm() {
        avmEnabled = false;
    }

    /** set values in this configuration from an XML */
    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "avm-enabled":
                            this.avmEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
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

    /** @return XML representation of this configuration */
    public String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);

            // start element vm
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("vm");

            // sub-element avm-enabled
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("avm-enabled");
            xmlWriter.writeCharacters(String.valueOf(avmEnabled));
            xmlWriter.writeEndElement();

            // close element vm
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

    /** @param avmEnabled whether AVM is enabled */
    public void setAvmEnabled(boolean avmEnabled) {
        this.avmEnabled = avmEnabled;
    }

    /** @return whether AVM is enabled */
    public boolean isAvmEnabled() {
        return this.avmEnabled;
    }
}
