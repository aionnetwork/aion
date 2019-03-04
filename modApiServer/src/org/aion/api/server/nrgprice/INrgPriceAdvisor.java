package org.aion.api.server.nrgprice;

import org.aion.interfaces.block.Block;
import org.aion.interfaces.tx.Transaction;

public interface INrgPriceAdvisor<BLK extends Block, TXN extends Transaction> {
    /* Is the recommendation engine hungry for more data?
     * Recommendations prescribed by engine while hungry are left up to the engine itself
     */
    boolean isHungry();

    /* Build the recommendation, one block at a time
     */
    void processBlock(BLK blk);

    /* Retrieve the recommendation stored in internal representation
     */
    long computeRecommendation();

    /* flush all history for recommendation engine
     */
    void flush();
}
