package org.aion.zero.impl.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class ConfigUtil {
    public static String readValue(final XMLStreamReader sr) throws XMLStreamException {
        StringBuilder str = new StringBuilder();
        readLoop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.CHARACTERS:
                    str.append(sr.getText());
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break readLoop;
            }
        }
        return str.toString();
    }

    public static void skipElement(final XMLStreamReader sr) throws XMLStreamException {
        while (sr.hasNext()) {
            int eventType = sr.next();
            if (eventType == XMLStreamReader.END_ELEMENT) {
                break;
            }
        }
    }
}
