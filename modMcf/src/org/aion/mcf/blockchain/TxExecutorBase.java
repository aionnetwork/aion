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
package org.aion.mcf.blockchain;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.IBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.slf4j.Logger;

/** Transaction executor base class. */
public abstract class TxExecutorBase<
        BLK extends IBlock<?, ?>,
        TX extends AbstractTransaction,
        BS extends IBlockStoreBase<?, ?>,
        TR extends AbstractTxReceipt<?>> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    protected TX tx;

    protected IRepositoryCache<?, ?> track;

    protected IRepositoryCache<?, ?> cacheTrack;

    protected BS blockStore;

    protected TR receipt;

    protected BLK currentBlock;
}
