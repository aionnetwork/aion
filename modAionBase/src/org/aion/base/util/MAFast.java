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

/**
 * http://www.daycounter.com/LabBook/Moving-Average.phtml
 *
 * The best way to remove noise and smooth out sensor data is to compute a moving average. The
 * moving average is a running average computer over a window the last N points of data. The average
 * is expressed as the sum of the last N points divided by N:
 *
 * MA[i]= sum(x[i]+x[i-(N-1)])/N
 *
 * The brute force way to compute this is to repeat the computation for every new data point. This
 * requires that N data points are stored, and N-1 additions are computed with a single divide.
 *
 * Another way to update the moving average is to merely subtract x[i-N-1]/N and to add x[i]/N to
 * the current MA.  This requires 2 additions and 2 divisions, but still this requires that N data
 * points be saved.
 *
 * The following method doesn't require any storage of previous data values, and minimizes divisions
 * which are computationally intensive. It works by subtracting out the mean each time, and adding
 * in a new point.
 *
 * MA*[i]= MA*[i-1] +X[i] - MA*[i-1]/N
 *
 * where MA* is the moving average*N.
 *
 * MA[i]= MA*[i]/N
 *
 * The advantage of this technique is that previous data values need not be stored. If N is a power
 * of 2 the division can be accomplished with a computationally efficient shift. So a single
 * division and subtraction and 2 shifts are all that are needed, rendering suitable for a simple
 * microcontroller.
 */
//public class MAFast {
//    private double MAStar; // MA*[i-1]
//    private volatile double MA; // MA[i]
//    private int N;
//
//    public MAFast(int N) {
//        this.N = N;
//        this.MAStar = 0D;
//        this.MA = 0D;
//    }
//
//    public synchronized double compute(int X) { // X[i]
//        double MAStar_i = MAStar + X + (MAStar / N);
//        MA = MAStar_i/N;
//        MAStar = MAStar_i;
//        return MA;
//    }
//
//    public double get() {
//        return MA;
//    }
//}
