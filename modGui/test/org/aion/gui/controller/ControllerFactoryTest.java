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

package org.aion.gui.controller;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelUpdateTimer;
import org.aion.os.KernelLauncher;
import org.junit.Before;
import org.junit.Test;

public class ControllerFactoryTest {
    private KernelConnection kernelConnection;
    private KernelLauncher kernelLauncher;
    private KernelUpdateTimer kernelUpdateTimer;
    private ConfigManipulator configManipulator;

    @Before
    public void before() {
        kernelConnection = mock(KernelConnection.class);
        kernelLauncher = mock(KernelLauncher.class);
        kernelUpdateTimer = mock(KernelUpdateTimer.class);
        configManipulator = mock(ConfigManipulator.class);
    }

    @Test
    public void testSettersAndGetters() {
        ControllerFactory unit =
                new ControllerFactory()
                        .withKernelConnection(kernelConnection)
                        .withKernelLauncher(kernelLauncher)
                        .withTimer(kernelUpdateTimer)
                        .withConfigManipulator(configManipulator);

        assertThat(unit.getKernelConnection(), is(kernelConnection));
        assertThat(unit.getKernelLauncher(), is(kernelLauncher));
        assertThat(unit.getTimer(), is(kernelUpdateTimer));
        assertThat(unit.getConfigManipulator(), is(configManipulator));
    }

    @Test
    public void testCallForClassWithZeroArgConstructor() {
        ControllerFactory unit =
                new ControllerFactory()
                        .withKernelConnection(kernelConnection)
                        .withKernelLauncher(kernelLauncher);
        ZeroArgConstructor result = (ZeroArgConstructor) unit.call(ZeroArgConstructor.class);
        assertThat(result instanceof ZeroArgConstructor, is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCallForClassWithoutZeroArgConstructor() {
        ControllerFactory unit =
                new ControllerFactory()
                        .withKernelConnection(kernelConnection)
                        .withKernelLauncher(kernelLauncher);
        unit.call(NoZeroArgConstructorClass.class);
    }

    @Test
    public void testCallForPredefinedClasses() {
        ControllerFactory unit =
                new ControllerFactory()
                        .withKernelConnection(kernelConnection)
                        .withKernelLauncher(kernelLauncher);

        assertThat(unit.call(DashboardController.class) instanceof DashboardController, is(true));
        assertThat(unit.call(SettingsController.class) instanceof SettingsController, is(true));
        /*assertThat(unit.call(ConnectivityStatusController.class)
                instanceof ConnectivityStatusController, is(true));
        assertThat(unit.call(PeerCountController.class) instanceof PeerCountController, is(true));
        assertThat(unit.call(SyncStatusController.class) instanceof
                SyncStatusController, is(true));*/
    }
}
