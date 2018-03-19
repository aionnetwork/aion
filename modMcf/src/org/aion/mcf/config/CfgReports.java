package org.aion.mcf.config;

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
    private long block_frequency;
    private boolean enable_heap_dumps;
    private int heap_dump_interval;

    public CfgReports() {
        // default configuration
        this.enable = false;
        this.path = "reports";
        this.block_frequency = 500L;
        this.enable_heap_dumps = false;
        this.heap_dump_interval = 100000;
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
                            this.block_frequency = Long.parseLong(Cfg.readValue(sr));
                            break;
                        case "enable_heap_dumps":
                            this.enable_heap_dumps = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "heap_dump_interval":
                            this.heap_dump_interval = Integer.parseInt(Cfg.readValue(sr));
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

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_heap_dumps");
            xmlWriter.writeCharacters(String.valueOf(this.isHeapDumpEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("heap_dump_interval");
            xmlWriter.writeCharacters(String.valueOf(this.getHeapDumpInterval()));
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
        return this.block_frequency;
    }

    public boolean isHeapDumpEnabled() {
        return enable_heap_dumps;
    }

    public int getHeapDumpInterval() {
        return this.heap_dump_interval;
    }
}
