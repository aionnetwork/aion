package org.aion.base.util;

/**
 * http://www.daycounter.com/LabBook/Moving-Average.phtml
 *
 * The best way to remove noise and smooth out sensor data is to compute a moving average.
 * The moving average is a running average computer over a window the last N points of data.
 * The average is expressed as the sum of the last N points divided by N:
 *
 *         MA[i]= sum(x[i]+x[i-(N-1)])/N
 *
 * The brute force way to compute this is to repeat the computation for every new data point.
 * This requires that N data points are stored, and N-1 additions are computed with a single divide.
 *
 * Another way to update the moving average is to merely subtract x[i-N-1]/N and
 * to add x[i]/N to the current MA.  This requires 2 additions and 2 divisions, but still this
 * requires that N data points be saved.
 *
 * The following method doesn't require any storage of previous data values,
 * and minimizes divisions which are computationally intensive. It works by subtracting out
 * the mean each time, and adding in a new point.
 *
 *         MA*[i]= MA*[i-1] +X[i] - MA*[i-1]/N
 *
 * where MA* is the moving average*N.
 *
 *         MA[i]= MA*[i]/N
 *
 * The advantage of this technique is that previous data values need not be stored.
 * If N is a power of 2 the division can be accomplished with a computationally efficient shift.
 * So a single division and subtraction and 2 shifts are all that are needed, rendering suitable
 * for a simple microcontroller.
 */
public class MAFast {
    private double MAStar; // MA*[i-1]
    private volatile double MA; // MA[i]
    private int N;

    public MAFast(int N) {
        this.N = N;
        this.MAStar = 0D;
        this.MA = 0D;
    }

    public synchronized double compute(int X) { // X[i]
        double MAStar_i = MAStar + X + (MAStar / N);
        MA = MAStar_i/N;
        MAStar = MAStar_i;
        return MA;
    }

    public double get() {
        return MA;
    }
}
