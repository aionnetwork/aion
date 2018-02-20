/*******************************************************************************
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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.base.timer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Modified to use caching thread pools instead
 * 
 * Note: cannot be shared between threads
 * 
 * @author yao
 *
 */
public class StackTimer implements ITimer {

    public static int NANO_INTERVAL = 10;

    // private final TimerThread thread = new TimerThread(stack);
    private final StackTimerRunnable timerRunnable;

    /**
     * We won't shutdown the thread pool until the very end, (when we need to
     * shutdown the program)
     */
    private static final ExecutorService timers = Executors.newCachedThreadPool(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable arg0) {
            Thread thread = new Thread(arg0, "StackTimer");
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    });

    /**
     * Called upon program exit, to shutdown the thread pool
     */
    public static void shutdownPool() {
        timers.shutdown();
    }

    public StackTimer() {
        timerRunnable = new StackTimerRunnable();
        timers.execute(timerRunnable);
    }

    @Override
    public void sched(TimerTask task) {
        if (task == null) {
            throw new RuntimeException("task cannot be null");
        }

        if (!(task.getTimeout() > 0)) {
            throw new RuntimeException("timeout has to be > 0");
        }
        task.start();
        timerRunnable.submit(task);
    }

    @Override
    public void shutdown() {
        timerRunnable.shutdown();
    }

    public boolean completed() {
        return timerRunnable.done();
    }

    protected StackTimerRunnable getTimerRunnable() {
        return this.timerRunnable;
    }

}
