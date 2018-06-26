package org.aion.mcf.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Configuration for modGui.  Represents the </code><code>gui</code> section of Aion kernel config.
 */
public class CfgGui {
    private CfgGuiLauncher cfgGuiLauncher;

    /** Constructor. */
    public CfgGui() {
        this.cfgGuiLauncher = new CfgGuiLauncher();
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

    public CfgGuiLauncher getCfgGuiLauncher() {
        return cfgGuiLauncher;
    }

    public void setCfgGuiLauncher(CfgGuiLauncher cfgGuiLauncher) {
        this.cfgGuiLauncher = cfgGuiLauncher;
    }
}
