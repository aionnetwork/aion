package org.aion.api.server.nrgprice;

import org.aion.api.server.nrgprice.INrgPriceAdvisor;
import org.aion.api.server.nrgprice.NrgBlockPriceStrategy;
import org.aion.base.type.*;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Serves as the recommendor of nrg prices based on some observation strategy
 * Currently uses the blockPrice strategy
 *
 * This class is thread safe: getNrgPrice() synchronized on object's intrinsic lock.
 *
 * @author ali sharif
 */
public class NrgOracle {
    private static final int BLKPRICE_WINDOW = 20;
    private static final int BLKPRICE_PERCENTILE = 60;

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private long tsLastCompute;
    private long lastBlkProcessed;

    private long recommendation;

    private INrgPriceAdvisor advisor;
    private IAionBlockchain blockchain;
    
    public NrgOracle(IAionBlockchain blockchain, IHandler handler, long nrgPriceDefault, long nrgPriceMax) {

        // get default and max nrg from the config
        this.recommendation = nrgPriceDefault;
        this.tsLastCompute = -1;
        this.lastBlkProcessed = -1;

        this.advisor = new NrgBlockPriceStrategy(nrgPriceDefault, nrgPriceMax, BLKPRICE_WINDOW, BLKPRICE_PERCENTILE);
        this.blockchain = blockchain;
    }

    // if we don't find any transaction within the last 64 blocks
    // (at 10s block time, ~10min), miners should be willing to accept transactions at defaultPrice
    private static final int MAX_BLK_TRAVERSE = 64;
    private void buildRecommendation() {
        AionBlock lastBlock = blockchain.getBestBlock();

        long blkDiff = lastBlock.getNumber() - lastBlkProcessed;
        if (blkDiff > BLKPRICE_WINDOW) {
            advisor.flush();
        }

        long blkTraverse = Math.min(MAX_BLK_TRAVERSE, blkDiff);

        int itr = 0;
        while (itr < blkTraverse) {
            advisor.processBlock(lastBlock);

            if (!advisor.isHungry()) break; // recommendation engine warmed up to give good advice

            // traverse up the chain to feed the recommendation engine
            long parentBlockNumber = lastBlock.getNumber() - 1;
            if (parentBlockNumber <= 0)
                break;

            lastBlock = blockchain.getBlockByHash(lastBlock.getParentHash());
            itr--;
        }

        recommendation = advisor.computeRecommendation();
        tsLastCompute = System.currentTimeMillis();
        lastBlkProcessed = lastBlock.getNumber();
    }

    /**
     * Lazy nrg price computation strategy: if you need the kernel to recommend nrg price, you should be prepared
     * to give up some compute-time on the caller thread (most likely api worker thread).
     *
     * Not an important enough computation to be a consumer on the event system.
     *
     * If multiple consumers want nrgPrice simultaneously, all will be blocked until the recommendation is built
     * and cached. Future consumers read the cached value until cache flush.
     */
    private static final long CACHE_FLUSH_MILLIS = 20_000; // 20s
    public synchronized long getNrgPrice() {
        long tsNow = System.currentTimeMillis();
        if (tsNow - tsLastCompute > CACHE_FLUSH_MILLIS) {
            buildRecommendation();
        }

        return recommendation;
    }
}
