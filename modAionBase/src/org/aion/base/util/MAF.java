
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

package org.aion.base.util;

import java.util.ArrayDeque;
import java.util.Queue;
/** @author: ali sharif Implements a simple Moving Average Filter This class is thread safe */
public class MAF {
    // rationale for using ArrayDeque - doc: 'This class is likely to be faster than Stack when used
    // as a stack,
    // and faster than LinkedList when used as a queue.'
    private final Queue<Double> myQ = new ArrayDeque<>();
    private final Object myQLock = new Object();

    // don't need count and total to be volatile since access to them is restricted by myQ's
    // intrinsic lock
    // and these values are only read under guard of myQ's intrinsic lock
    // using a separate counter here to track Queue size, in-case the used queue's size method is
    // NOT a constant-time operation
    private int count = 0;
    private double total = 0;

    // using volatile here OK as this is the "independent observations" pattern
    // 1. Writes to the variable do not depend on its current value.
    // 2. The variable does not participate in invariants with other variables.
    private volatile double movingAvg = 0;

    private int window;

    public MAF(int window) {
        // can't have a window less than 2 elements
        if (window < 2) this.window = 2;
        else this.window = window;
    }

    public void add(double value) {
        synchronized (myQLock) {
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
