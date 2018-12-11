package org.aion.mcf.mine;

public interface IMineRunner {

    void startMining();

    void stopMining();

    void delayedStartMining(int sec);

    boolean isMining();

    double getHashrate();

    void shutdown();
}
