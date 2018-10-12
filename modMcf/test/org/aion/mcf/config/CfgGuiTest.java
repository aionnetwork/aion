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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.CharSource;
import java.io.IOException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.Test;

/**
 * Test {@link CfgGui}
 */
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

    @Test
    public void toXML() throws Exception {
        CfgGui unit = new CfgGui();
        CfgGuiLauncher cfgGuiLauncher = mock(CfgGuiLauncher.class);
        unit.setCfgGuiLauncher(cfgGuiLauncher);
        when(cfgGuiLauncher.toXML()).thenReturn("<cfg-gui-part/>");

        String result = unit.toXML();
        assertThat(result, is("")); // cfg is hidden for now
//        assertThat(result, is(
//                "\r\n\t<gui>\r\n" +
//                        "\t<cfg-gui-part/>\r\n" +
//                        "\t</gui>"
//        ));
    }
}