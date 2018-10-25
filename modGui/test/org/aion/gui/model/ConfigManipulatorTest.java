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

package org.aion.gui.model;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import org.aion.mcf.config.Cfg;
import org.aion.os.KernelLauncher;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.config.dynamic.ConfigProposalResult;
import org.aion.zero.impl.config.dynamic.InFlightConfigReceiver;
import org.aion.zero.impl.config.dynamic.InFlightConfigReceiverMBean;
import org.junit.Before;
import org.junit.Test;

public class ConfigManipulatorTest {
    private ConfigManipulator.JmxCaller jmxCaller;
    private ConfigManipulator.FileLoaderSaver fileLoaderSaver;
    private Cfg cfg;
    private KernelLauncher kernelLauncher;

    @Before
    public void before() {
        jmxCaller = mock(ConfigManipulator.JmxCaller.class);
        ;
        fileLoaderSaver = mock(ConfigManipulator.FileLoaderSaver.class);
        cfg = new CfgAion();
        kernelLauncher = mock(KernelLauncher.class);
    }

    @Test
    public void testGetLastLoadedContent() throws Exception {
        String configFileContents = "<fake><config><stuff>";
        when(fileLoaderSaver.load(new File(cfg.getBasePath() + "/config/config.xml")))
                .thenReturn(configFileContents);
        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        assertThat(unit.loadFromConfigFile(), is(configFileContents));
        assertThat(unit.getLastLoadedContent(), is(configFileContents));
    }

    @Test
    public void testLoadFromConfigFileWhenError() throws Exception {
        when(fileLoaderSaver.load(new File(cfg.getBasePath() + "/config/config.xml")))
                .thenThrow(new IOException());
        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        assertThat(unit.loadFromConfigFile(), is("<Could not load config file>"));
    }

    @Test
    public void testApplyNewConfig() throws Exception {
        String xml = "<anyGoodXml/>";
        ConfigProposalResult configProposalResult = new ConfigProposalResult(true);
        when(jmxCaller.sendConfigProposal(xml)).thenReturn(configProposalResult);

        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        ApplyConfigResult result = unit.applyNewConfig(xml);
        assertThat(result.isSucceeded(), is(true));
    }

    @Test
    public void testApplyNewConfigWhenXmlErrors() {
        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        ApplyConfigResult result = unit.applyNewConfig("</not<xml");
        assertThat(result.isSucceeded(), is(false));
    }

    @Test
    public void testApplyNewConfigWhenBackupError() throws Exception {
        String xml = "<anyGoodXml/>";
        ConfigProposalResult configProposalResult = new ConfigProposalResult(true);
        InFlightConfigReceiverMBean proxy = mock(InFlightConfigReceiver.class);
        when(jmxCaller.sendConfigProposal(xml)).thenReturn(configProposalResult);
        doThrow(new IOException())
                .when(fileLoaderSaver)
                .save(any(), eq(new File(cfg.getBasePath() + "/config/config.backup.xml")));

        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        ApplyConfigResult result = unit.applyNewConfig("<anyGoodXml/>");

        assertThat(result.isSucceeded(), is(false));
        assertThat(result.getCause() instanceof IOException, is(false));
    }

    @Test
    public void testApplyNewConfigWhenSaveNewConfigError() throws Exception {
        String xml = "<anyGoodXml/>";
        ConfigProposalResult configProposalResult = new ConfigProposalResult(true);
        InFlightConfigReceiverMBean proxy = mock(InFlightConfigReceiver.class);
        when(jmxCaller.sendConfigProposal(xml)).thenReturn(configProposalResult);
        doThrow(new IOException())
                .when(fileLoaderSaver)
                .save(any(), eq(new File(cfg.getBasePath() + "/config/config.xml")));

        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        ApplyConfigResult result = unit.applyNewConfig("<anyGoodXml/>");

        assertThat(result.isSucceeded(), is(true));
    }

    @Test
    public void testApplyNewConfigRejectedIfKernelRunning() {
        String xml = "<anyGoodXml/>";
        when(kernelLauncher.hasLaunchedInstance()).thenReturn(true);

        ConfigManipulator unit =
                new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver, jmxCaller);
        ApplyConfigResult result = unit.applyNewConfig(xml);
        assertThat(result.isSucceeded(), is(false));
    }

    // test cases below will be used when ConfigManipulator actually sends configs to the kernel
    // feature is disabled for now, so commenting out the tests.

    //    @Test
    //    public void testApplyNewConfigWhenProxyReturnsError() throws Exception {
    //        String xml = "<anyGoodXml/>";
    //        Exception proxyError = new ArrayIndexOutOfBoundsException();
    //        ConfigProposalResult configProposalResult = new ConfigProposalResult(false,
    // proxyError);
    //        InFlightConfigReceiverMBean proxy = mock(InFlightConfigReceiver.class);
    //        when(jmxCaller.sendConfigProposal(xml)).thenReturn(configProposalResult);
    //
    //        ConfigManipulator unit = new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver,
    // jmxCaller);
    //        ApplyConfigResult result = unit.applyNewConfig(xml);
    //        assertThat(result.isSucceeded(), is(false));
    //        assertThat(result.getCause(), is(proxyError));
    //    }
    //
    //    @Test
    //    public void testApplyNewConfigWhenProxyThrowsRollbackException() throws Exception {
    //        String xml = "<anyGoodXml/>";
    //        RollbackException rbe = new RollbackException("the sky is falling",
    //                Collections.singletonList(new IOException()));
    //        InFlightConfigReceiverMBean proxy = mock(InFlightConfigReceiver.class);
    //        when(jmxCaller.sendConfigProposal(xml)).thenThrow(rbe);
    //
    //        ConfigManipulator unit = new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver,
    // jmxCaller);
    //        ApplyConfigResult result = unit.applyNewConfig(xml);
    //        assertThat(result.isSucceeded(), is(false));
    //        assertThat(result.getCause(), is(rbe));
    //    }
    //
    //    @Test
    //    public void testApplyNewConfigWhenJmxConnectionFails() throws Exception {
    //        String xml = "<anyGoodXml/>";
    //        Exception jmxError = new MalformedObjectNameException();
    //        when(jmxCaller.sendConfigProposal(xml)).thenThrow(jmxError);
    //
    //        ConfigManipulator unit = new ConfigManipulator(cfg, kernelLauncher, fileLoaderSaver,
    // jmxCaller);
    //        ApplyConfigResult result = unit.applyNewConfig(xml);
    //        assertThat(result.isSucceeded(), is(false));
    //        assertThat(result.getCause(), is(jmxError));
    //    }
}
