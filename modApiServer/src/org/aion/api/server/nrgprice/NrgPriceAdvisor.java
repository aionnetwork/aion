package org.aion.api.server.nrgprice;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;

public abstract class NrgPriceAdvisor<BLK extends IBlock, TXN extends ITransaction> implements INrgPriceAdvisor<BLK, TXN> {

    protected long defaultPrice;
    protected long maxPrice;

    /* Impose a min & max thresholds on the recommendation output
     */
    NrgPriceAdvisor(long defaultPrice, long maxPrice) {
        this.defaultPrice = defaultPrice;
        this.maxPrice = maxPrice;
    }
}
