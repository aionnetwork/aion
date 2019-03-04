package org.aion.api.server.nrgprice;

import org.aion.interfaces.block.Block;
import org.aion.interfaces.tx.Transaction;

public abstract class NrgPriceAdvisor<BLK extends Block, TXN extends Transaction>
        implements INrgPriceAdvisor<BLK, TXN> {

    protected long defaultPrice;
    protected long maxPrice;

    /* Impose a min & max thresholds on the recommendation output
     */
    public NrgPriceAdvisor(long defaultPrice, long maxPrice) {
        this.defaultPrice = defaultPrice;
        this.maxPrice = maxPrice;
    }
}
