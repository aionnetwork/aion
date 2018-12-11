package org.aion.base.timer;

/**
 * Dummy timer for call methods (since I don't have a good way to allocate resources for them yet)
 *
 * @author yao
 */
public class TimerDummy implements ITimer {

    @Override
    public void shutdown() {
        // TODO Auto-generated method stub

    }

    @Override
    public void sched(TimerTask timer) {
        // TODO Auto-generated method stub

    }
}
