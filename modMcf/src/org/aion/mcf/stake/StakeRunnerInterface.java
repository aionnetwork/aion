package org.aion.mcf.stake;

public interface StakeRunnerInterface {
    void startStaking();

    void stopStaking();

    void delayedStartStaking(int sec);

    boolean isStaking();

    void shutdown();

    void fireRunnerStarted();

    void fireRunnerStopped();
}
