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
 ******************************************************************************/
package org.aion.base.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 * @author: ali sharif
 * Implements a simple Moving Average Filter
 * This class is thread safe
 */
public class MAF {
    private final Queue<Double> myQ = new ConcurrentLinkedQueue<Double>();

    // don't need count and total to be volatile since access to them is restricted by myQ's intrinsic lock
    // and these values are only read under guard of myQ's intrinsic lock
    // using a separate counter here to track Queue size, in-case the used queue's size method is NOT a constant-time operation
    int count = 0;
    double total = 0;

    // using volatile here OK as this is the "independent observations" pattern
    // 1. Writes to the variable do not depend on its current value.
    // 2. The variable does not participate in invariants with other variables.
    volatile double movingAvg = 0;

    private int window;

    public MAF(int window) {
        // can't have a window less than 2 elements
        if (window < 2)
            this.window = 2;
        else
            this.window = window;
    }

    public void add(double value) {
        synchronized (myQ) {
            if (myQ.offer(value)) {
                total += value;
                count++;
            }
            if (count > window) {
                total -= myQ.poll();
                count--;
            }
            movingAvg = total / count;
        }
    }

    public double getAverage() {
        return movingAvg;
    }
}
