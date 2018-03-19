package org.aion.mcf.config;

import org.aion.mcf.config.Cfg;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Printing reports for debugging purposes.
 *
 * @author Alexandra Roatis
 */
public class CfgReports {

    private boolean enable;
    private String path;
    private long blockFrequency;

    public CfgReports() {
        // default configuration
        this.enable = false;
        this.path = "reports";
        this.blockFrequency = 500L;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "enable":
                            this.enable = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "path":
                            this.path = Cfg.readValue(sr);
                            break;
                        case "block_frequency":
                            this.blockFrequency = Long.parseLong(Cfg.readValue(sr));
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
            xmlWriter.writeStartElement("reports");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable");
            xmlWriter.writeCharacters(String.valueOf(this.isEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("path");
            xmlWriter.writeCharacters(this.getPath());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("block_frequency");
            xmlWriter.writeCharacters(String.valueOf(this.getBlockFrequency()));
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

    public boolean isEnabled() {
        return enable;
    }

    public String getPath() {
        return this.path;
    }

    public long getBlockFrequency() {
        return this.blockFrequency;
    }
}
