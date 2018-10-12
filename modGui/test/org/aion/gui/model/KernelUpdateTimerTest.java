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