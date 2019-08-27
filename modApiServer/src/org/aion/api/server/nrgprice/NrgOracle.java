package org.aion.api.server.nrgprice;

import org.aion.api.server.nrgprice.strategy.NrgBlockPrice;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.IPowChain;
import org.slf4j.Logger;

/**
 * Serves as the recommendor of nrg prices based on some observation strategy Currently uses the
 * blockPrice strategy
 *
 * <p>This class is thread safe: getNrgPrice() synchronized on object's intrinsic lock.
 *
 * @author ali sharif
 */
public class NrgOracle {

    public enum Strategy {
        SIMPLE,
        BLK_PRICE
    }

    private static final int BLKPRICE_WINDOW = 20;
    private static final int BLKPRICE_PERCENTILE = 60;

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private long lastBlkProcessed;
    private long recommendation;
    private long nrgPriceDefault;
    private Strategy strategy;

    private INrgPriceAdvisor advisor;
    private IPowChain blockchain;

    public NrgOracle(
            IPowChain blockchain, long nrgPriceDefault, long nrgPriceMax, Strategy strategy) {

        // get default and max nrg from the config
        this.recommendation = nrgPriceDefault;
        this.lastBlkProcessed = -1;
        this.nrgPriceDefault = nrgPriceDefault;
        this.strategy = strategy;

        switch (strategy) {
            case BLK_PRICE:
                this.advisor =
                        new NrgBlockPrice(
                                nrgPriceDefault, nrgPriceMax, BLKPRICE_WINDOW, BLKPRICE_PERCENTILE);
                this.blockchain = blockchain;
                break;
            default:
                this.advisor = null;
                this.blockchain = null;
                break;
        }
    }

    // if we don't find any transaction within the last N blocks
    // (at 10s block time, ~10min), miners should be willing to accept transactions at my
    // defaultPrice
    private static final int MAX_BLK_TRAVERSE = 64;

    private void buildRecommendation() {
        long firstBlockNum = blockchain.getBestBlock().getNumber();
        Block lastBlock = blockchain.getBestBlock();

        advisor.flush();

        long blkTraverse = MAX_BLK_TRAVERSE;

        while (blkTraverse > 0) {
            advisor.processBlock(lastBlock);

            if (!advisor.isHungry()) break;

            // traverse up the chain to feed the recommendation engine
            long parentBlockNumber = lastBlock.getNumber() - 1;
            if (parentBlockNumber <= 0) break;

            lastBlock = blockchain.getBlockByHash(lastBlock.getParentHash());
            blkTraverse--;
        }

        recommendation = advisor.computeRecommendation();
        lastBlkProcessed = firstBlockNum;
    }

    /**
     * Lazy nrg price computation strategy: if you need the kernel to recommend nrg price, you
     * should be prepared to give up some compute-time on the caller thread (most likely api worker
     * thread).
     *
     * <p>Not an important enough computation to be a consumer on the event system.
     *
     * <p>If multiple consumers want nrgPrice simultaneously, all will be blocked until the
     * recommendation is built and cached. Future consumers read the cached value until cache flush.
     */
    private static final long CACHE_FLUSH_BLKS = 2;

    public synchronized long getNrgPrice() {
        switch (strategy) {
            case BLK_PRICE:
                try {
                    long blkNow = blockchain.getBestBlock().getNumber();
                    if (blkNow - lastBlkProcessed >= CACHE_FLUSH_BLKS) {
                        buildRecommendation();
                    }
                } catch (Exception e) {
                    LOG.error(
                            "<nrg-oacle - buildRecommendation() threw. returning default nrg recommendation just-in-case");
                    return nrgPriceDefault;
                }
                break;
            default:
                return nrgPriceDefault;
        }

        return recommendation;
    }
}
