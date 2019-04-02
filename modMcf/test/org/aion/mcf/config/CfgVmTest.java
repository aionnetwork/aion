package org.aion.mcf.config;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import com.google.common.io.CharSource;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.junit.Test;

public class CfgVmTest {

    @Test
    public void testFromXML() throws Exception {
        String testXml =
                "<vm><avm-enabled>true</avm-enabled><unrecognizedThing>should_be_ignored</unrecognizedThing></vm>";
        XMLStreamReader xmlStream =
                XMLInputFactory.newInstance()
                        .createXMLStreamReader(CharSource.wrap(testXml).openStream());
        CfgVm unit = new CfgVm();
        unit.fromXML(xmlStream);
    }

    @Test
    public void testToXML() {
        assertThat(new CfgVm().toXML().isEmpty(), is(false));
    }
}
