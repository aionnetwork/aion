package org.aion.mcf.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
                        case "avmenabled":
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
        // Config hidden for now; so return nothing.
        return "";
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
