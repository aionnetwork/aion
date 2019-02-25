/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aion.base.timer;

/** @author jin */
public class TimerTask {

    public TimerTask(long timeOut) {
        this.timeOut = timeOut;
    }

    public Object lock = new Object();

    private long timeOut;
    private long startTime;
    private long timeoutAt;
    private long endTime;

    private boolean isTimeOut;
    private boolean isDone;

    public long getEndTime() {
        return this.endTime;
    }

    public long getTimeout() {
        return this.timeOut;
    }

    public void start() {
        this.startTime = System.nanoTime();
        this.endTime = this.startTime + this.timeOut;
    }

    public long getRemaining() {
        return Math.max(getEndTime() - System.nanoTime(), 1L);
    }

    public boolean isTimeOut() {
        return isTimeOut;
    }

    public synchronized void setDone() {
        this.isDone = true;
    }

    public boolean getDone() {
        return this.isDone;
    }

    protected synchronized void setTimeOut() {
        if (isTimeOut == false) {
            isTimeOut = true;
            timeoutAt = System.nanoTime();
        }
    }

    public long getTimeoutDuration() {
        return this.timeoutAt - this.startTime;
    }
}
