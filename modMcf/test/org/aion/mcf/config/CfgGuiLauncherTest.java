package org.aion.mcf.config;

import com.google.common.io.CharSource;
import org.junit.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Test {@link CfgGuiLauncher}*/
public class CfgGuiLauncherTest {
    @Test
    public void testGetterSetter() {
        CfgGuiLauncher unit = new CfgGuiLauncher();

        String aionSh = "aionSh";
        boolean autodetectJavaRuntime = true;
        String javaHome = "javaHome";
        String wd = "workingDir";

        unit.setAionSh(aionSh);
        unit.setAutodetectJavaRuntime(autodetectJavaRuntime);
        unit.setJavaHome(javaHome);
        unit.setWorkingDir(wd);

        assertThat(unit.getAionSh(), is(aionSh));
        assertThat(unit.isAutodetectJavaRuntime(), is(autodetectJavaRuntime));
        assertThat(unit.getJavaHome(), is(javaHome));
        assertThat(unit.getWorkingDir(), is(wd));
    }

    @Test
    public void testFromXml() throws IOException, XMLStreamException {
        String testXml = "<launcher>" +
                "<autodetect>true</autodetect><java-home>javaHome</java-home>" +
                "<aion-sh>aionSh</aion-sh>" +
                "<working-dir>workingDir</working-dir>" +
                "<keep-kernel-on-exit>true</keep-kernel-on-exit>" +
                "</launcher>";

        System.out.println(testXml);
        XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                .createXMLStreamReader(CharSource.wrap(testXml).openStream());
        CfgGuiLauncher unit = new CfgGuiLauncher();

        unit.fromXML(xmlStream);

        assertThat(unit.isAutodetectJavaRuntime(), is(true));
        assertThat(unit.getJavaHome(), is("javaHome"));
        assertThat(unit.getAionSh(), is("aionSh"));
        assertThat(unit.getWorkingDir(), is("workingDir"));
        assertThat(unit.isKeepKernelOnExit(), is(true));
    }
}