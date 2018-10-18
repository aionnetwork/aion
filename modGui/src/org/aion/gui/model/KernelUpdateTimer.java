package org.aion.gui.model;

import org.aion.gui.util.AionConstants;
import org.aion.gui.util.DataUpdater;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class KernelUpdateTimer {
    private final ScheduledExecutorService timer;
    private ScheduledFuture<?> execution;
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    public KernelUpdateTimer(ScheduledExecutorService timer) {
        this.timer = timer;
    }

    /** Start timer.  If already started, this has no effect. */
    public void start() {
        LOG.debug("Started timer");
        if(execution == null) {
            execution = timer.scheduleAtFixedRate(
                    new DataUpdater(),
                    AionConstants.BLOCK_MINING_TIME_MILLIS,
//                    3 * AionConstants.BLOCK_MINING_TIME_MILLIS,
                    AionConstants.BLOCK_MINING_TIME_MILLIS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /** Stop timer.  If already stopped, this has no effect. */
    public void stop() {
        LOG.debug("Stopped timer");
        if(execution != null) {
            execution.cancel(true);
        }
        execution = null;
    }
}
