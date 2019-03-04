package org.aion.mcf.blockchain;

import java.util.Collection;
import java.util.List;
import org.aion.interfaces.block.Block;
import org.aion.mcf.types.AbstractBlockHeaderWrapper;

/**
 * Sync queue interface.
 *
 * @param <BLK>
 * @param <BHW>
 */
public interface ISyncQueue<BLK extends Block<?, ?>, BHW extends AbstractBlockHeaderWrapper<?>> {

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
