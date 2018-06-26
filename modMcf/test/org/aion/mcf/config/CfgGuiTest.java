package org.aion.mcf.config;

import com.google.common.io.CharSource;
import org.junit.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.Reader;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Test {@link CfgGui} */
public class CfgGuiTest {
    @Test
    public void testGetterSetter() {
        CfgGui unit = new CfgGui();
        CfgGuiLauncher cfgGuiLauncher = new CfgGuiLauncher();

        unit.setCfgGuiLauncher(cfgGuiLauncher);
        assertThat(unit.getCfgGuiLauncher(), is(cfgGuiLauncher));
    }

    @Test
    public void fromXML() throws IOException, XMLStreamException {
        String testXml = "<gui><launcher><stuff-here-does-not-matter /></launcher></gui>";
        XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                .createXMLStreamReader(CharSource.wrap(testXml).openStream());

        CfgGui unit = new CfgGui();
        CfgGuiLauncher cfgGuiLauncher = mock(CfgGuiLauncher.class);

        unit.setCfgGuiLauncher(cfgGuiLauncher);

        unit.fromXML(xmlStream);
        verify(cfgGuiLauncher).fromXML(xmlStream);
    }
}