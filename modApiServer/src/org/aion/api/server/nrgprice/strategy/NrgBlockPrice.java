package org.aion.api.server.nrgprice.strategy;

import org.aion.api.server.nrgprice.NrgPriceAdvisor;
import org.aion.base.type.Address;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Implementation of strategy adopted by Ethereum mainstream clients in early 2018 of using the
 * notion of 'blockPrice': minimum nrgPriced transaction in a block that did not originate from the proposer
 * of that block. This algorithm recommends the Nth percentile blockPrice observed over the last M blocks
 *
 * This design has a bias toward the lower end of the threshold of nrg prices required
 * for txns to be accepted into blocks (heuristically)
 *
 * A transaction based design alternatively has been observed in the wild to have a positive feedback
 * effect where large numbers of people following the recommendation will tend the recommendation upward
 *
 * This class is NOT thread-safe
 * Policy: holder class (NrgOracle) should provide any concurrency guarantees it needs to
 *
 * @author ali sharif
 */
public class NrgBlockPrice extends NrgPriceAdvisor<AionBlock, AionTransaction> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
    private ArrayBlockingQueue<Long> blkPriceQ;

    // enforce writes through buildRecommendation() to happen serially if multiple objects try to hold reference
    // to this object. ie. enforce only one writer to populate the block list at a time
    // private Object recommendationBuilderLock = new Object();

    int percentile;
    int windowSize;
    int recommendationIndex;

    public NrgBlockPrice(long defaultPrice, long maxPrice, int windowSize, int percentile) {
        super(defaultPrice, maxPrice);

        // clamp the percentile measure
        if (percentile < 0)
            this.percentile = 0; // pick the smallest value
        else if (percentile > 100)
            this.percentile = 100; // pick the largest value
        else
            this.percentile = percentile;

        // clamp the windowSize at the bottom at 1
        if (windowSize < 1)
            this.windowSize = 1; // when no elements in window, just return the minPrice
        else {
            this.windowSize = windowSize;

            // percentile enforced to be between 0-100, so i should exist within array bounds
            this.recommendationIndex = (int) Math.round(windowSize * percentile / 100d);
            if (this.recommendationIndex > (windowSize - 1)) this.recommendationIndex = windowSize - 1;
        }

        blkPriceQ = new ArrayBlockingQueue<>(windowSize);
    }

    @Override
    // in order to have good recommendations, we try to keep the blkPriceQ full
    public boolean isHungry() {
        return (blkPriceQ.remainingCapacity() > 0);
    }

    // notion of "block price" = lowest gas price for all transactions in a block, exluding miner's own transactions
    // returns null if block is empty, invalid input, block filled only with miner's own transactions
    @SuppressWarnings("Duplicates")
    private Long getBlkPrice(AionBlock blk) {
        if (blk == null)
            return null;

        List<AionTransaction> txns = blk.getTransactionsList();
        Address coinbase = blk.getCoinbase();

        // there is nothing stopping nrg price to be 0. don't explicitly enforce non-zero nrg.
        Long minNrg = null;
        for(AionTransaction txn : txns) {
            if (coinbase.compareTo(txn.getFrom()) != 0) {
                long nrg = txn.getNrgPrice();
                if (minNrg == null || nrg < minNrg)
                    minNrg = nrg;
            }
        }

        return minNrg;
    }

    /* Onus on the holder of an NrgPriceAdvisor instance to provide guarantees on:
    * 1) Blocks provided in the right order
    * 2) No duplicate blocks provided
    *
    * No mechanism anywhere to invalidate computed blkPrice values here (in case of chain re-orgs, etc.)
    * Rationale: if the block was mined and accepted by some part of the network that included this node,
    * then that block represents work done by the network; the person who did the work should have made the
    * greediest gasPrice decisions wrt. including transactions in the block. Therefore, those side-chained
    * blocks are valid signals by miners of acceptable nrg prices
    */
    @Override
    @SuppressWarnings("Duplicates")
    public void processBlock(AionBlock blk) {
        if (blk == null) return;

        Long blkPrice = getBlkPrice(blk);

        if (blkPrice != null) {
            if (!blkPriceQ.offer(blkPrice)) {
                blkPriceQ.poll();
                if (!blkPriceQ.offer(blkPrice))
                    LOG.error("NrgBlockPrice - problem with backing queue implementation");
                    // alternatively, we could throw here
            }
        }
    }

    @Override
    public void flush() {
        blkPriceQ.clear();
    }

    @Override
    public long computeRecommendation() {
        // if I'm still hungry, then I can't give a good enough prediction yet.
        // if I'm still hungry, and if the chain is being supported by proof of work, the miners will accept
        // transaction with any gasPrice > some minimum threshold they've set internally.
        if (isHungry())
            return defaultPrice;

        // let the backing syncronized collection do the locking for us.
        Long[] blkPrice = blkPriceQ.toArray(new Long[blkPriceQ.size()]);
        Arrays.sort(blkPrice);

        long recommendation = blkPrice[recommendationIndex];

        // clamp the recommendation at the top if necessary
        // no minimum clamp since we can let the price go as low as the network deems profitable
        if (recommendation > maxPrice)
            return maxPrice;

        return recommendation;
    }
}
