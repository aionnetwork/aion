package org.aion.engine.impl;

import org.aion.generic.IBlockchainEngine;
import org.aion.generic.IGenericAionChain;
import org.aion.zero.impl.blockchain.IChainInstancePOW;

public class Aion0BlockchainEngine implements IBlockchainEngine {

    private IChainInstancePOW blockchain;

    public Aion0BlockchainEngine(IChainInstancePOW  blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public IGenericAionChain getAionChain() {
        return blockchain;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }
}
