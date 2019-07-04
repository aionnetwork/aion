package org.aion.api.server.nrgprice;

import org.aion.base.Transaction;
import org.aion.mcf.blockchain.Block;

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
