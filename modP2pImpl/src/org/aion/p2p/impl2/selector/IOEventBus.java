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

package org.aion.p2p.impl2.selector;

import java.nio.channels.SelectableChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class IOEventBus {
    private Queue<Runnable> queue;

    private static final int MAX_PENDING_TASKS = 16;

    public IOEventBus() {
        this.queue = new LinkedBlockingQueue<>();

    }

    public void addEvent(Runnable run) {
        this.queue.offer(run);
    }

    public List<Runnable> retrieveAllEvents() {
        List<Runnable> taskList = new LinkedList<>();
        int i = 0;
        while(!this.queue.isEmpty() && i < MAX_PENDING_TASKS) {
            taskList.add(this.queue.poll());
            i++;
        }
        return taskList;
    }

    public boolean hasTasks() {
        return !this.queue.isEmpty();
    }
}
