/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.api.server.nrgprice;

import org.aion.api.server.nrgprice.strategy.NrgBlockPrice;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;
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
    private IAionBlockchain blockchain;

    public NrgOracle(
            IAionBlockchain blockchain, long nrgPriceDefault, long nrgPriceMax, Strategy strategy) {

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
        AionBlock lastBlock = blockchain.getBestBlock();

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
