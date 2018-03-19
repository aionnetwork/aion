/*******************************************************************************
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
 *     
 ******************************************************************************/
package org.aion.zero.impl.tx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.aion.mcf.types.AbstractTransaction;

@SuppressWarnings("rawtypes")
public class TxBroadcaster<TX extends AbstractTransaction, TXTASK extends AbstractTxTask> {

    private TxBroadcaster() {
    }

    static private TxBroadcaster instance;

    static public TxBroadcaster getInstance() {
        if (instance == null) {
            instance = new TxBroadcaster();
        }
        return instance;
    }

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    @SuppressWarnings("unchecked")
    public Future<List<TX>> submitTransaction(TXTASK task) {
        return executor.submit(task);
    }
}
