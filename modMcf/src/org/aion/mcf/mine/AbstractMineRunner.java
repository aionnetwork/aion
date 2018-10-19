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
package org.aion.mcf.mine;

import org.aion.base.type.IBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/** Abstract Miner. */
public abstract class AbstractMineRunner<BLK extends IBlock<?, ?>> implements IMineRunner {

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
