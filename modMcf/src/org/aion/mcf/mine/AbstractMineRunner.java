package org.aion.mcf.mine;

import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.slf4j.Logger;

/** Abstract Miner. */
public abstract class AbstractMineRunner<BLK extends Block<?>> implements IMineRunner {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    protected int cpuThreads;

    protected boolean isMining;

    protected volatile BLK miningBlock;

    public void setCpuThreads(int cpuThreads) {
        this.cpuThreads = cpuThreads;
    }

    public boolean isMining() {
        return isMining;
    }

    protected abstract void fireMinerStarted();

    protected abstract void fireMinerStopped();
}
