package org.aion.generic;

public interface IBlockchainEngine {
    IGenericAionChain getAionChain();

    void start();

    void stop();
}
