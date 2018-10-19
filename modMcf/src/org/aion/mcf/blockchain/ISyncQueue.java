/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
package org.aion.mcf.blockchain;

import java.util.Collection;
import java.util.List;
import org.aion.base.type.IBlock;
import org.aion.mcf.types.AbstractBlockHeaderWrapper;

/**
 * Sync queue interface.
 *
 * @param <BLK>
 * @param <BHW>
 */
public interface ISyncQueue<BLK extends IBlock<?, ?>, BHW extends AbstractBlockHeaderWrapper<?>> {

    /** Wanted headers */
    interface HeadersRequest {

        long getStart();

        int getCount();

        boolean isReverse();
    }

    /** Wanted blocks */
    interface BlocksRequest<BW extends AbstractBlockHeaderWrapper<?>> {

        List<BlocksRequest> split(int count);

        List<BW> getBlockHeaders();
    }

    /** Returns wanted headers request */
    HeadersRequest requestHeaders();

    /**
     * Adds received headers. Headers need to verified. The list can be in any order and shouldn't
     * correspond to prior headers request
     */
    void addHeaders(Collection<BHW> headers);

    /** Returns wanted blocks hashes */
    BlocksRequest<BHW> requestBlocks(int maxSize);

    /**
     * Adds new received blocks to the queue The blocks need to be verified but can be passed in any
     * order and need not correspond to prior returned block request
     *
     * @return blocks ready to be imported in the valid import order.
     */
    List<BLK> addBlocks(Collection<BLK> blocks);

    /** Returns approximate header count waiting for their blocks */
    int getHeadersCount();
}
