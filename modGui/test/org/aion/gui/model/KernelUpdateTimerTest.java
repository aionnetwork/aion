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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.aion.gui.util.DataUpdater;
import org.junit.Test;

public class KernelUpdateTimerTest {

    @Test
    public void start() {
        ScheduledExecutorService ses = mock(ScheduledExecutorService.class);
        when(ses.scheduleAtFixedRate(any(DataUpdater.class), anyLong(), anyLong(), any()))
            .thenReturn(mock(ScheduledFuture.class));
        KernelUpdateTimer unit = new KernelUpdateTimer(ses);

        unit.start();
        verify(ses, times(1)).scheduleAtFixedRate(
            any(DataUpdater.class), anyLong(), anyLong(), any());
        unit.start();
        verify(ses, times(1)).scheduleAtFixedRate(
            any(DataUpdater.class), anyLong(), anyLong(), any());
    }

    @Test
    public void stop() {
        ScheduledExecutorService ses = mock(ScheduledExecutorService.class);
        ScheduledFuture sf = mock(ScheduledFuture.class);
        when(ses.scheduleAtFixedRate(any(DataUpdater.class), anyLong(), anyLong(), any()))
            .thenReturn(sf);
        KernelUpdateTimer unit = new KernelUpdateTimer(ses);

        unit.start();
        unit.stop();
        verify(sf, times(1)).cancel(true);
        unit.stop();
        verify(sf, times(1)).cancel(true);
    }
}