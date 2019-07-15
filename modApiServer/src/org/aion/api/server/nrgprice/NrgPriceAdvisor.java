package org.aion.api.server.nrgprice;

public abstract class NrgPriceAdvisor implements INrgPriceAdvisor {

    protected long defaultPrice;
    protected long maxPrice;

    /* Impose a min & max thresholds on the recommendation output
     */
    public NrgPriceAdvisor(long defaultPrice, long maxPrice) {
        this.defaultPrice = defaultPrice;
        this.maxPrice = maxPrice;
    }
}
