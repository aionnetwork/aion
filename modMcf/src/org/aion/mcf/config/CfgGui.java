package org.aion.mcf.config;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Configuration for modGui.  Represents the </code><code>gui</code> section of Aion kernel config.
 */
public class CfgGui {
    private CfgGuiLauncher cfgGuiLauncher;

    /** Constructor. */
    public CfgGui() {
        this.cfgGuiLauncher = CfgGuiLauncher.DEFAULT_CONFIG;
    }

    /** Populate this object from XML data */
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
}
